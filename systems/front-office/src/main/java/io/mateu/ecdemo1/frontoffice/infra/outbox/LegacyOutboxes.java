package io.mateu.ecdemo1.frontoffice.infra.outbox;

import io.mateu.ecdemo1.frontoffice.application.PmsStays;
import io.mateu.ecdemo1.messaging.MessagingProperties;
import io.mateu.ecdemo1.messaging.MessagingSchema;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The front office's own outbox and inbox tables, from before the shared ones: {@code command_outbox}
 * and {@code audit_outbox}, relayed by relays of their own, and {@code command_inbox}, the command ids
 * taken. What they still hold is carried over to the shared tables, so nothing asked before the change
 * is lost and nothing taken before it is taken again:
 *
 * <ul>
 *   <li>every message not relayed yet goes into the shared outbox, oldest first, with its topic, key
 *       and time — and leaves the old table in the same transaction, so it is relayed once from here;
 *   <li>every command id taken goes into the shared inbox, under {@link PmsStays#CONSUMER}.
 * </ul>
 *
 * <p>At startup, and every minute for a while after it: during a rolling update the previous pod
 * writes to the old tables until it stops, and what it wrote is carried over too. The old tables keep
 * what was already relayed — the history — and are otherwise left alone.
 */
@Component
public class LegacyOutboxes {

  static final Logger log = LoggerFactory.getLogger(LegacyOutboxes.class);
  static final Duration WINDOW = Duration.ofMinutes(20);

  final JdbcTemplate jdbc;
  final TransactionTemplate transactions;
  final MessagingProperties messaging;
  final Clock clock = Clock.systemUTC();
  final Instant started = Instant.now();

  public LegacyOutboxes(JdbcTemplate jdbc, PlatformTransactionManager transactions, MessagingProperties messaging) {
    this.jdbc = jdbc;
    this.transactions = new TransactionTemplate(transactions);
    this.messaging = messaging;
  }

  record Pending(String table, String idColumn, String id, String topic, String key, String type, String payload,
                 Instant createdAt) {}

  @EventListener(ApplicationReadyEvent.class)
  public void atStartup() {
    carryOver();
  }

  @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
  public void whileThePreviousPodMayStillWrite() {
    if (Duration.between(started, clock.instant()).compareTo(WINDOW) < 0) {
      carryOver();
    }
  }

  public synchronized void carryOver() {
    try {
      transactions.executeWithoutResult(status -> {
        var moved = moveMessages();
        var seen = copyTakenIds();
        if (moved > 0 || seen > 0) {
          log.info("Old outboxes: {} message(s) not relayed yet moved to {}, {} command id(s) taken copied to {}",
              moved, messaging.outbox().table(), seen, messaging.inbox().table());
        }
      });
    } catch (RuntimeException e) {
      log.warn("Old outboxes not carried over yet: {}", e.toString());
    }
  }

  int moveMessages() {
    var pending = new ArrayList<Pending>();
    if (MessagingSchema.tableExists(jdbc, "command_outbox")) {
      pending.addAll(jdbc.query("select message_id, topic, message_key, payload, created_at from command_outbox "
              + "where published_at is null for update",
          (rs, n) -> new Pending("command_outbox", "message_id", rs.getString("message_id"), rs.getString("topic"),
              rs.getString("message_key"), "command", rs.getString("payload"), rs.getTimestamp("created_at").toInstant())));
    }
    if (MessagingSchema.tableExists(jdbc, "audit_outbox")) {
      pending.addAll(jdbc.query("select action_id, payload, created_at from audit_outbox where published_at is null for update",
          (rs, n) -> new Pending("audit_outbox", "action_id", rs.getString("action_id"), "audit",
              rs.getString("action_id"), "AuditedAction", rs.getString("payload"), rs.getTimestamp("created_at").toInstant())));
    }
    pending.sort(Comparator.comparing(Pending::createdAt).thenComparing(Pending::id));
    for (var p : pending) {
      jdbc.update("insert into " + messaging.outbox().table() + " (binding, message_key, event_type, payload, created_at, "
              + "attempts, abandoned) values (?, ?, ?, ?, ?, 0, false)",
          p.topic(), p.key(), p.type(), p.payload(), OffsetDateTime.ofInstant(p.createdAt(), ZoneOffset.UTC));
      jdbc.update("delete from " + p.table() + " where " + p.idColumn() + " = ?", p.id());
    }
    return pending.size();
  }

  int copyTakenIds() {
    if (!MessagingSchema.tableExists(jdbc, "command_inbox")) {
      return 0;
    }
    var table = messaging.inbox().table();
    return jdbc.update("insert into " + table + " (consumer, event_id, received_at) "
        + "select ?, c.command_id, c.taken_at from command_inbox c "
        + "where not exists (select 1 from " + table + " i where i.consumer = ? and i.event_id = c.command_id)",
        PmsStays.CONSUMER, PmsStays.CONSUMER);
  }
}
