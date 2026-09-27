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
  /** The CRS adapter's: the reservations nobody arrived for. */
  public static final String NO_SHOW_REPORTS = "no-show-reports";

  static final JsonMapper JSON = JsonMapper.builder().build();

  final Outbox outbox;

  public CommandOutbox(Outbox outbox) {
    this.outbox = outbox;
  }

  /** In the caller's transaction, if there is one: the command leaves only if the decision was saved. */
  public void append(String topic, String key, Object command) {
    outbox.append(topic, key, command.getClass().getSimpleName(), JSON.writeValueAsString(command), null);
  }

  /** Every command written to a topic, oldest first: what the tests read. */
  public List<OutboxMessage> all(String topic) {
    return outbox.messages(topic);
  }
}
