package io.mateu.ecdemo1.integrations.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import io.mateu.workflow.ddd.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * What must leave only if the decision that produced it was saved: the message that resumes a
 * process once its causes are resolved, the start of its successor, and the notifications.
 */
@Component
@RequiredArgsConstructor
public class Outbox {

    public static final String ENGINE = "outboxUpstream";
    public static final String NOTIFICATIONS = "notifications";
    public static final String AUDIT = "audit";

    final OutboxMessageRepository repository;
    final ObjectMapper objectMapper;
    final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void appendToEngine(DomainEvent event) {
        write(ENGINE, event.partitionKey(), event.getClass().getSimpleName(), serialise(DomainEvent.class, event));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void appendNotification(NotificationRequested notification) {
        write(NOTIFICATIONS, notification.dedupKey(), "NotificationRequested",
                serialise(NotificationRequested.class, notification));
    }

    /** A person's auditable action, for the audit service (HLA F016). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendAudit(AuditedAction action) {
        write(AUDIT, action.actionId(), "AuditedAction", serialise(AuditedAction.class, action));
    }

    private String serialise(Class<?> as, Object value) {
        try {
            return objectMapper.writerFor(as).writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + value, e);
        }
    }

    private void write(String binding, String key, String type, String payload) {
        var message = new OutboxMessageEntity();
        message.binding = binding;
        message.messageKey = key;
        message.eventType = type;
        message.payload = payload;
        message.createdAt = clock.instant();
        repository.save(message);
    }
}
