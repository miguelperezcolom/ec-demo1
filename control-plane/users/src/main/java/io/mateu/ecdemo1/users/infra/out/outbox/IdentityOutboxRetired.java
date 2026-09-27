package io.mateu.ecdemo1.users.infra.out.outbox;

import io.mateu.ecdemo1.messaging.MessagingProperties;
import io.mateu.ecdemo1.messaging.MessagingSchema;
import io.mateu.ecdemo1.users.application.usecases.user.identity.IdentityOutboxAppender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * The identity outbox had a table and a relay of its own ({@code identity_outbox}); the changes go
 * through the shared outbox now. What the old table still held undelivered — and not abandoned — is
 * carried over, oldest first, with its time, and leaves the old table in the same transaction: every
 * change asked for before the switch reaches the provider, once from here. Delivered and abandoned rows
 * stay where they are, as the history and for someone to look at.
 *
 * <p>At startup, and every minute for a while after it: during a rolling update the previous pod
 * appends to the old table until it stops, and what it wrote is carried over too.
 */
@Component
@Slf4j
public class IdentityOutboxRetired {

    static final Duration WINDOW = Duration.ofMinutes(20);

    final JdbcTemplate jdbc;
    final TransactionTemplate transactions;
    final MessagingProperties messaging;
    final Instant started = Instant.now();

    public IdentityOutboxRetired(JdbcTemplate jdbc, PlatformTransactionManager transactions,
                                 MessagingProperties messaging) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactions);
        this.messaging = messaging;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void atStartup() {
        carryOver();
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void whileThePreviousPodMayStillWrite() {
        if (Duration.between(started, Instant.now()).compareTo(WINDOW) < 0) {
            carryOver();
        }
    }

    public synchronized void carryOver() {
        try {
            var moved = transactions.execute(status -> {
                if (!MessagingSchema.tableExists(jdbc, "identity_outbox")) {
                    return 0;
                }
                var rows = jdbc.queryForList("""
                        select id, aggregate_id, event_type, payload, occurred_at from identity_outbox
                        where delivered_at is null and abandoned = false
                        order by occurred_at, id
                        for update""");
                for (var row : rows) {
                    var occurred = row.get("occurred_at");
                    var at = occurred instanceof Timestamp t ? t.toInstant()
                            : occurred instanceof OffsetDateTime o ? o.toInstant() : Instant.now();
                    jdbc.update("insert into " + messaging.outbox().table()
                                    + " (binding, message_key, event_type, payload, created_at, attempts, abandoned)"
                                    + " values (?, ?, ?, ?, ?, 0, false)",
                            IdentityOutboxAppender.DESTINATION, row.get("aggregate_id"), row.get("event_type"),
                            row.get("payload"), OffsetDateTime.ofInstant(at, ZoneOffset.UTC));
                    jdbc.update("delete from identity_outbox where id = ?", row.get("id"));
                }
                return rows.size();
            });
            if (moved != null && moved > 0) {
                log.info("identity_outbox: {} undelivered change(s) carried over to {}", moved,
                        messaging.outbox().table());
            }
        } catch (RuntimeException e) {
            log.warn("identity_outbox not carried over yet: {}", e.toString());
        }
    }
}
