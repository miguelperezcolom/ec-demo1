package io.mateu.ecdemo1.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The library on one database, with a clock the test moves and a transport that records what it sends. */
class Fixture {

    static class MovableClock extends Clock {
        Instant now = Instant.parse("2026-09-28T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    record Sent(OutboxMessage message, Map<String, byte[]> headers) {
    }

    /** Records what it sends; fails for the payloads it is told to. */
    static class RecordingTransport implements OutboxTransport {
        final List<Sent> sent = new ArrayList<>();
        final Set<String> failing = new HashSet<>();

        @Override
        public void send(OutboxMessage message, Map<String, byte[]> headers) {
            if (failing.contains(message.payload())) {
                throw new IllegalStateException("refused " + message.payload());
            }
            sent.add(new Sent(message, headers));
        }

        List<String> payloads() {
            return sent.stream().map(s -> s.message().payload()).toList();
        }
    }

    final DataSource dataSource;
    final JdbcTemplate jdbc;
    final TransactionTemplate transactions;
    final MovableClock clock = new MovableClock();
    final RecordingTransport transport = new RecordingTransport();
    final MessagingProperties properties;
    final MessagingSchema schema;
    final Outbox outbox;
    final Inbox inbox;
    final OutboxRelay relay;

    Fixture(DataSource dataSource, MessagingProperties properties, TraceContexts traces) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.properties = properties;
        this.schema = new MessagingSchema(jdbc, properties);
        schema.create();
        this.outbox = new Outbox(jdbc, properties, traces, clock);
        this.inbox = new Inbox(jdbc, transactions, properties, clock, schema.postgres());
        this.relay = new OutboxRelay(jdbc, transactions, properties, transport, traces, clock, schema.postgres());
    }

    static MessagingProperties properties(String outboxTable, String inboxTable, int maxAttempts,
                                          boolean continueAfterFailure) {
        return new MessagingProperties(
                new MessagingProperties.Outbox(outboxTable, true, null, 100, Duration.ofDays(7), null, maxAttempts,
                        Duration.ofSeconds(1), Duration.ofSeconds(8), continueAfterFailure, null),
                new MessagingProperties.Inbox(inboxTable), true);
    }

    void append(String key, String payload) {
        transactions.executeWithoutResult(s -> outbox.append("events", key, payload));
    }
}
