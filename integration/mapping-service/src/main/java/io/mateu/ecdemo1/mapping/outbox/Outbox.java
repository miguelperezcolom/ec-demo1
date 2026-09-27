package io.mateu.ecdemo1.mapping.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import io.mateu.ecdemo1.integration.model.notification.NotificationResolved;
import io.mateu.ecdemo1.messaging.engine.EngineOutbox;
import io.mateu.workflow.ddd.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * What must leave only if the decision that produced it was saved: the message that resumes a
 * process once its causes are resolved, the start of its successor, and the notifications.
 *
 * <p>This service's messages, in its words; the outbox itself — the table, the relay, the trace
 * context carried to the record — is the shared one ({@link io.mateu.ecdemo1.messaging.Outbox}).
 */
@Component
@RequiredArgsConstructor
public class Outbox {

    public static final String ENGINE = "outboxUpstream";
    public static final String NOTIFICATIONS = "notifications";
    public static final String AUDIT = "audit";
    public static final String RESOLUTIONS = "resolutions";

    final io.mateu.ecdemo1.messaging.Outbox outbox;
    final EngineOutbox engine;
    final ObjectMapper objectMapper;
    final Clock clock;

    /**
     * A request to the engine. A process started here — a successor, relaunched — joins the trace it
     * was asked for in: the current context goes in the request's {@code traceContext}, which the
     * engine keeps with the process and hands on to every task it dispatches for it.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendToEngine(DomainEvent event) {
        engine.append(event);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void appendNotification(NotificationRequested notification) {
        write(NOTIFICATIONS, notification.dedupKey(), "NotificationRequested",
                serialise(NotificationRequested.class, notification));
    }

    /** What the notifications about this subject asked for is no longer waiting: they close in every inbox. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendResolution(String subject, String resolvedBy) {
        write(RESOLUTIONS, subject, "NotificationResolved",
                serialise(NotificationResolved.class, new NotificationResolved(subject, resolvedBy, clock.instant())));
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
        outbox.append(binding, key, type, payload, null);
    }
}
