package io.mateu.ecdemo1.frontoffice.infra.audit;

import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import io.mateu.ecdemo1.messaging.Outbox;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * The front office's auditable actions — what the reception agent carried out, or was refused — on
 * their way to the audit service: written to the shared outbox ({@link Outbox}) once done, and relayed
 * to the {@code audit} topic, where the audit service keeps them — the same topic, and the same
 * AuditedAction, as the control plane's services. Keyed by the action's id: the outbox delivers at
 * least once and the audit service records an id only once.
 */
@Repository
public class AuditOutbox {

  /** The service the audit trail names as the one the action was taken on. */
  public static final String SERVICE = "front-office";
  static final String TOPIC = "audit";

  static final JsonMapper JSON = JsonMapper.builder().build();

  final Outbox outbox;

  public AuditOutbox(Outbox outbox) {
    this.outbox = outbox;
  }

  public void append(AuditedAction action) {
    write(action);
    APPENDED.get()[0]++;
  }

  /** Into the outbox, in the caller's transaction. */
  protected void write(AuditedAction action) {
    outbox.append(TOPIC, action.actionId(), "AuditedAction", JSON.writeValueAsString(action), null);
  }

  static final ThreadLocal<long[]> APPENDED = ThreadLocal.withInitial(() -> new long[1]);

  /**
   * How many actions this thread has audited so far: compared before and after a piece of work, it says
   * whether the work audited itself — so whoever runs it for someone else audits it only if it did not.
   */
  public static long appendedOnThisThread() {
    return APPENDED.get()[0];
  }
}
