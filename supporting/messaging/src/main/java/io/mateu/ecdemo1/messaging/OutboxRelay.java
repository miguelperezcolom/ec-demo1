package io.mateu.ecdemo1.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends what the outbox holds, oldest first, and marks it sent.
 *
 * <p>A pass takes a batch of the due messages, locked ({@code skip locked} on PostgreSQL, so a second
 * instance takes others instead of waiting), in the order they were written, and sends each through
 * the service's {@link OutboxTransport}, which returns only once the destination has it. The order
 * kept is per key: the messages of one key go one after another, and one that fails holds back the
 * rest of its key — in this pass, and in the next ones while it waits out its backoff — while other
 * keys go on. A failed message is tried again after a backoff that doubles each time up to a ceiling,
 * and is abandoned (kept, with its last error, never tried again) after {@code max-attempts} failures
 * when there is such a limit.
 *
 * <p>The outcome of each message is recorded in the pass's transaction; if that transaction does not
 * commit, what it sent is sent again on the next pass. A message can therefore go twice, never zero
 * times: consumers deduplicate.
 */
public class OutboxRelay {

    static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    final JdbcTemplate jdbc;
    final TransactionTemplate transactions;
    final MessagingProperties.Outbox settings;
    final OutboxTransport transport;
    final TraceContexts traces;
    final Clock clock;
    final String lockClause;

    public OutboxRelay(JdbcTemplate jdbc, TransactionTemplate transactions, MessagingProperties properties,
                       OutboxTransport transport, TraceContexts traces, Clock clock, boolean postgres) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.settings = properties.outbox();
        this.transport = transport;
        this.traces = traces;
        this.clock = clock;
        this.lockClause = postgres ? "for update of t skip locked" : "for update";
    }

    /** What one pass did. */
    public record Pass(int sent, int failed, int held) {
        public int taken() {
            return sent + failed + held;
        }
    }

    /** Relays the due messages, a batch after another while batches come full and nothing fails. */
    public void relayPending() {
        for (int i = 0; i < 20; i++) {
            var pass = relayOnce();
            if (pass.failed() > 0 || pass.taken() < settings.batchSize()) {
                return;
            }
        }
    }

    /** One pass: a batch, in one transaction. */
    public Pass relayOnce() {
        var pass = transactions.execute(status -> relayBatch());
        return pass == null ? new Pass(0, 0, 0) : pass;
    }

    Pass relayBatch() {
        var table = settings.table();
        var now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        var batch = jdbc.query("""
                select t.* from %1$s t
                where t.published_at is null and t.abandoned = false
                  and (t.next_attempt_at is null or t.next_attempt_at <= ?)
                  and not exists (select 1 from %1$s e
                                  where e.message_key = t.message_key and e.seq < t.seq
                                    and e.published_at is null and e.abandoned = false
                                    and e.next_attempt_at > ?)
                order by t.seq
                limit ?
                %2$s""".formatted(table, lockClause), Outbox.ROW, now, now, settings.batchSize());
        int sent = 0, failed = 0, held = 0;
        var heldKeys = new HashSet<String>();
        for (var message : batch) {
            if (message.key() != null && heldKeys.contains(message.key())) {
                held++;
                continue;
            }
            try {
                send(message);
                jdbc.update("update %s set published_at = ?, last_error = null where seq = ?".formatted(table),
                        OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), message.seq());
                sent++;
                log.debug("Relayed {} #{} to {}", message.type(), message.seq(), message.destination());
            } catch (Exception e) {
                failed++;
                failed(message, e);
                if (message.key() != null) {
                    heldKeys.add(message.key());
                }
                if (!settings.continueAfterFailure()) {
                    break;
                }
            }
        }
        return new Pass(sent, failed, held);
    }

    void send(OutboxMessage message) throws Exception {
        traces.continuing(message.traceparent(), message.tracestate(), "outbox " + message.destination(), trace -> {
            var headers = new LinkedHashMap<String, byte[]>();
            message.headers().forEach((name, value) -> headers.put(name, value.getBytes(StandardCharsets.UTF_8)));
            // byte[], as the W3C propagators on the other side read them; a String header would reach
            // them JSON-quoted.
            trace.forEach((name, value) -> headers.put(name, value.getBytes(StandardCharsets.UTF_8)));
            transport.send(message, Map.copyOf(headers));
        });
    }

    void failed(OutboxMessage message, Exception e) {
        var attempts = message.attempts() + 1;
        var abandoned = settings.maxAttempts() > 0 && attempts >= settings.maxAttempts();
        var next = clock.instant().plus(settings.backoff(attempts));
        var error = String.valueOf(e);
        if (error.length() > 2000) {
            error = error.substring(0, 2000);
        }
        jdbc.update("update %s set attempts = ?, last_error = ?, next_attempt_at = ?, abandoned = ? where seq = ?"
                        .formatted(settings.table()), attempts, error, OffsetDateTime.ofInstant(next, ZoneOffset.UTC),
                abandoned, message.seq());
        if (abandoned) {
            log.error("Outbox message {} #{} to {} abandoned after {} attempts: {}", message.type(), message.seq(),
                    message.destination(), attempts, error);
        } else {
            log.warn("Outbox message {} #{} to {} not sent (attempt {}), next try at {}: {}", message.type(),
                    message.seq(), message.destination(), attempts, next, error);
        }
    }

    /** Deletes what was sent longer ago than the retention. */
    public int cleanup() {
        var before = clock.instant().minus(settings.retention());
        var purged = transactions.execute(status -> jdbc.update(
                "delete from %s where published_at is not null and published_at < ?".formatted(settings.table()),
                OffsetDateTime.ofInstant(before, ZoneOffset.UTC)));
        if (purged != null && purged > 0) {
            log.info("Purged {} relayed outbox messages", purged);
        }
        return purged == null ? 0 : purged;
    }
}
