package io.mateu.ecdemo1.frontoffice.infra.outbox;

import io.mateu.ecdemo1.messaging.Outbox;
import io.mateu.ecdemo1.messaging.OutboxMessage;
import java.util.List;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * What the front office orders other services, until it reaches their topic: a command is written to
 * the shared outbox ({@link Outbox}) in the transaction of the decision that asks for it, and its relay
 * sends it on, with the trace it was written in. The desk never waits for the other service, and a
 * broker that is down loses nothing. Each command carries its own id, on which the receiver
 * deduplicates (its inbox).
 */
@Repository
public class CommandOutbox {

  /** The customer MDM's commands: the desk's changes to a customer, the documents it scans. */
  public static final String CUSTOMER_COMMANDS = "customer-commands";
  /**
   * The front office's own events: what its desk did — check-in, check-out, no-show — for the PMS,
   * the master of the stay, to record (the pms-fo integration takes them).
   */
  public static final String FRONT_OFFICE_EVENTS = io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeEvent.TOPIC;

  static final JsonMapper JSON = JsonMapper.builder().build();

  final Outbox outbox;

  public CommandOutbox(Outbox outbox) {
    this.outbox = outbox;
  }

  /** In the caller's transaction, if there is one: the command leaves only if the decision was saved. */
  public void append(String topic, String key, Object command) {
    outbox.append(topic, key, command.getClass().getSimpleName(), JSON.writeValueAsString(command), null);
  }

  /** An event of the desk, in the caller's transaction: it leaves only if the decision was saved. */
  public void appendEvent(io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeEvent event) {
    outbox.append(FRONT_OFFICE_EVENTS, event.key(), event.getClass().getSimpleName(),
        JSON.writerFor(io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeEvent.class).writeValueAsString(event), null);
  }

  /** Every command written to a topic, oldest first: what the tests read. */
  public List<OutboxMessage> all(String topic) {
    return outbox.messages(topic);
  }
}
