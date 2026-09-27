package io.mateu.ecdemo1.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Where a consumer deduplicates what it receives. Delivery is at least once — every reconnection, every
 * outbox pass that did not commit, repeats something — so a consumer records each message id in the
 * same transaction as what it does about it, and does nothing with one it has already recorded: the
 * record and the work commit together, or neither does and the redelivery finds the id unrecorded.
 */
public class Inbox {

    final JdbcTemplate jdbc;
    final TransactionTemplate transactions;
    final String table;
    final Clock clock;
    final boolean postgres;

    public Inbox(JdbcTemplate jdbc, TransactionTemplate transactions, MessagingProperties properties, Clock clock,
                 boolean postgres) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.table = properties.inbox().table();
        this.clock = clock;
        this.postgres = postgres;
    }

    /**
     * Runs {@code work} the first time {@code consumer} sees {@code messageId}, recording the id in the
     * same transaction: the caller's when there is one, a new one otherwise. A message with no id cannot
     * be deduplicated, and its work always runs.
     *
     * @return whether the work ran
     */
    public boolean once(String messageId, String consumer, Runnable work) {
        return once(messageId, consumer, () -> {
            work.run();
            return Boolean.TRUE;
        }).isPresent();
    }

    /** As {@link #once(String, String, Runnable)}, with the work's result — empty when it did not run. */
    public <T> Optional<T> once(String messageId, String consumer, Supplier<T> work) {
        return transactions.execute(status -> {
            if (messageId != null && !messageId.isBlank() && !firstTime(consumer, messageId)) {
                return Optional.empty();
            }
            return Optional.ofNullable(work.get());
        });
    }

    /**
     * Records the id in the current transaction.
     *
     * @return true the first time this consumer sees it, false on every repetition
     */
    public boolean firstTime(String consumer, String messageId) {
        var now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (postgres) {
            return jdbc.update("insert into %s (consumer, event_id, received_at) values (?, ?, ?) on conflict do nothing"
                    .formatted(table), consumer, messageId, now) == 1;
        }
        return jdbc.update("""
                insert into %1$s (consumer, event_id, received_at)
                select ?, ?, ? from (values (1)) x
                where not exists (select 1 from %1$s where consumer = ? and event_id = ?)""".formatted(table),
                consumer, messageId, now, consumer, messageId) == 1;
    }

    /** Whether this consumer has seen the id. */
    public boolean seen(String consumer, String messageId) {
        var count = jdbc.queryForObject("select count(*) from %s where consumer = ? and event_id = ?".formatted(table),
                Integer.class, consumer, messageId);
        return count != null && count > 0;
    }
}
