package io.mateu.ecdemo1.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Messages that leave only if the transaction that produced them commits. {@link #append} writes the
 * message into the outbox table through the caller's transaction — whatever its transaction manager:
 * JPA's, JDBC's — so the decision and its message commit together or not at all; the
 * {@link OutboxRelay} sends it afterwards, and retries until it goes. Delivery is therefore at least
 * once, and consumers deduplicate ({@link Inbox}).
 *
 * <p>The trace context current when it is appended is stored with it, and the relay puts it back on
 * the record: the consumer continues the trace that wrote it.
 */
public class Outbox {

    final JdbcTemplate jdbc;
    final MessagingProperties properties;
    final TraceContexts traces;
    final Clock clock;
    final boolean requireTransaction;

    public Outbox(JdbcTemplate jdbc, MessagingProperties properties, TraceContexts traces, Clock clock) {
        this(jdbc, properties, traces, clock, false);
    }

    /**
     * @param requireTransaction whether appending with no transaction active is an error (rather than a
     *                           message committed on its own)
     */
    public Outbox(JdbcTemplate jdbc, MessagingProperties properties, TraceContexts traces, Clock clock,
                  boolean requireTransaction) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.traces = traces;
        this.clock = clock;
        this.requireTransaction = requireTransaction;
    }

    public void append(String destination, String key, String payload) {
        append(destination, key, null, payload, Map.of());
    }

    public void append(String destination, String key, String payload, Map<String, String> headers) {
        append(destination, key, null, payload, headers);
    }

    /**
     * Writes a message, in the caller's transaction when there is one.
     *
     * @param destination where the relay sends it (a binding, a topic)
     * @param key         what keeps it in order with the others of its key; null for none
     * @param type        what it is, for the log and the reader; null takes the destination
     * @param payload     the message
     * @param headers     to go on the record, besides the trace context
     */
    public void append(String destination, String key, String type, String payload, Map<String, String> headers) {
        if (destination == null || payload == null) {
            throw new IllegalArgumentException("A message needs a destination and a payload");
        }
        if (requireTransaction && !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Appending to the outbox outside a transaction");
        }
        var trace = traces.current();
        jdbc.update("""
                        insert into %s (binding, message_key, event_type, payload, headers, created_at, traceparent,
                                        tracestate, attempts, abandoned)
                        values (?, ?, ?, ?, ?, ?, ?, ?, 0, false)""".formatted(properties.outbox().table()),
                destination, key, type == null ? destination : type, payload, Headers.encode(headers),
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                trace.get(TraceContexts.TRACEPARENT), trace.get(TraceContexts.TRACESTATE));
    }

    /** Every message in the outbox, oldest first — what tests and consoles read. */
    public List<OutboxMessage> messages() {
        return jdbc.query("select * from %s order by seq".formatted(properties.outbox().table()), ROW);
    }

    /** Every message written for a destination, oldest first — what tests and consoles read. */
    public List<OutboxMessage> messages(String destination) {
        return jdbc.query("select * from %s where binding = ? order by seq".formatted(properties.outbox().table()),
                ROW, destination);
    }

    /** The messages not sent yet, abandoned ones included. */
    public long pendingCount() {
        var count = jdbc.queryForObject("select count(*) from %s where published_at is null"
                .formatted(properties.outbox().table()), Long.class);
        return count == null ? 0 : count;
    }

    static final RowMapper<OutboxMessage> ROW = (rs, n) -> {
        var created = rs.getObject("created_at", OffsetDateTime.class);
        var published = rs.getObject("published_at", OffsetDateTime.class);
        return new OutboxMessage(rs.getLong("seq"), rs.getString("binding"), rs.getString("message_key"),
                rs.getString("event_type"), rs.getString("payload"), Headers.decode(rs.getString("headers")),
                rs.getString("traceparent"), rs.getString("tracestate"), rs.getInt("attempts"),
                created == null ? null : created.toInstant(), published == null ? null : published.toInstant());
    };
}
