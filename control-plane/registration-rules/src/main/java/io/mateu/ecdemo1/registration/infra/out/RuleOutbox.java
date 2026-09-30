package io.mateu.ecdemo1.registration.infra.out;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged;
import io.mateu.ecdemo1.messaging.Outbox;
import io.mateu.ecdemo1.registration.application.RegistrationRules;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every change of a rule, and its audit record, to the shared outbox in the change's own transaction:
 * its relay publishes them through {@code ruleEvents} (registration-rules, keyed by the scope) and
 * {@code auditEvents} (audit).
 */
@Component
public class RuleOutbox implements RegistrationRules.Events {

    public static final String RULES = "ruleEvents";
    public static final String AUDIT = "auditEvents";

    /** Where the outbox is written: the shared one, or — in a test — anything that takes the message. */
    public interface Sink {
        void append(String binding, String key, String type, String payload);
    }

    final Sink sink;
    final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public RuleOutbox(Outbox outbox, ObjectMapper objectMapper) {
        this((binding, key, type, payload) -> outbox.append(binding, key, type, payload, null), objectMapper);
    }

    public RuleOutbox(Sink sink, ObjectMapper objectMapper) {
        this.sink = sink;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(RegistrationRuleChanged event) {
        write(event);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void audit(AuditedAction action) {
        sink.append(AUDIT, action.actionId(), "AuditedAction", json(action));
    }

    /** The message as it goes: the record, with the application's mapper. */
    public void write(RegistrationRuleChanged event) {
        sink.append(RULES, event.key(), "RegistrationRuleChanged", json(event));
    }

    String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + o, e);
        }
    }
}
