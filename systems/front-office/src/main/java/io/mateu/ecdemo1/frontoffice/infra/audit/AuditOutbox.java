package io.mateu.ecdemo1.frontoffice.infra.audit;

import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * The front office's auditable actions — what the reception agent carried out, or was refused — until
 * they reach the audit service. An action is written here once it is done, and {@link AuditRelay}
 * sends it on: the action never waits for Kafka, and a broker that is down loses nothing.
 */
@Repository
public class AuditOutbox {

  /** The service the audit trail names as the one the action was taken on. */
  public static final String SERVICE = "front-office";

  static final JsonMapper JSON = JsonMapper.builder().build();

  /** One action still to be relayed: its id and the AuditedAction as JSON. */
  public record Entry(String actionId, String payload) {}

  final JdbcTemplate jdbc;

  public AuditOutbox(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void append(AuditedAction action) {
    jdbc.update("insert into audit_outbox (action_id, payload, created_at) values (?, ?, ?)",
        action.actionId(), JSON.writeValueAsString(action), Timestamp.from(action.at()));
  }

  /** The oldest actions not relayed yet. */
  public List<Entry> pending(int max) {
    return jdbc.query("select action_id, payload from audit_outbox where published_at is null order by created_at",
        (rs, n) -> new Entry(rs.getString("action_id"), rs.getString("payload")))
        .stream().limit(max).toList();
  }

  public void published(String actionId, Instant at) {
    jdbc.update("update audit_outbox set published_at = ? where action_id = ?", Timestamp.from(at), actionId);
  }
}
