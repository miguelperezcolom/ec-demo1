package io.mateu.ecdemo1.frontoffice.infra.outbox;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * What the front office orders other services, until it reaches their topic — the same pattern as the
 * {@link io.mateu.ecdemo1.frontoffice.infra.audit.AuditOutbox}: a command is written here in the
 * transaction of the decision that asks for it, and {@link CommandRelay} sends it on. The desk never
 * waits for the other service, and a broker that is down loses nothing. Each command carries its own
 * id, on which the receiver deduplicates (its inbox).
 */
@Repository
public class CommandOutbox {

  /** The customer MDM's commands: the desk's changes to a customer, the documents it scans. */
  public static final String CUSTOMER_COMMANDS = "customer-commands";
  /** The CRS adapter's: the reservations nobody arrived for. */
  public static final String NO_SHOW_REPORTS = "no-show-reports";

  static final JsonMapper JSON = JsonMapper.builder().build();

  /** One command still to be relayed. */
  public record Entry(String messageId, String topic, String key, String payload) {}

  final JdbcTemplate jdbc;
  final Clock clock = Clock.systemUTC();

  public CommandOutbox(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** In the caller's transaction, if there is one: the command leaves only if the decision was saved. */
  public void append(String topic, String key, String messageId, Object command) {
    jdbc.update("insert into command_outbox (message_id, topic, message_key, payload, created_at) values (?, ?, ?, ?, ?)",
        messageId, topic, key, JSON.writeValueAsString(command), Timestamp.from(clock.instant()));
  }

  /** The oldest commands not relayed yet. */
  public List<Entry> pending(int max) {
    return jdbc.query("select message_id, topic, message_key, payload from command_outbox where published_at is null "
            + "order by created_at, message_id",
        (rs, n) -> new Entry(rs.getString("message_id"), rs.getString("topic"), rs.getString("message_key"),
            rs.getString("payload")))
        .stream().limit(max).toList();
  }

  /** Every command written to a topic, oldest first: what the tests read. */
  public List<Entry> all(String topic) {
    return jdbc.query("select message_id, topic, message_key, payload from command_outbox where topic = ? "
            + "order by created_at, message_id",
        (rs, n) -> new Entry(rs.getString("message_id"), rs.getString("topic"), rs.getString("message_key"),
            rs.getString("payload")), topic);
  }

  public void published(String messageId, Instant at) {
    jdbc.update("update command_outbox set published_at = ? where message_id = ?", Timestamp.from(at), messageId);
  }
}
