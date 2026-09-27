package io.mateu.ecdemo1.mapping.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import io.mateu.ecdemo1.integration.model.notification.NotificationResolved;
import io.mateu.ecdemo1.mapping.tracing.Traces;
import io.mateu.workflow.ddd.DomainEvent;
import io.mateu.workflow.dtos.TraceContext;
import io.mateu.workflow.dtos.events.integration.ProcessCreationRequested;
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
    public static final String RESOLUTIONS = "resolutions";

    final OutboxMessageRepository repository;
    final ObjectMapper objectMapper;
    final Clock clock;
    final Traces traces;

    /**
     * A request to the engine. A process started here — a successor, relaunched — joins the trace it
     * was asked for in: the current context goes in the request's {@code traceContext}, which the
     * engine keeps with the process and hands on to every task it dispatches for it.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendToEngine(DomainEvent event) {
        if (event instanceof ProcessCreationRequested request && request.traceContext() == null) {
            var current = traces.current();
            var context = TraceContext.of(current.get(Traces.TRACEPARENT), current.get(Traces.TRACESTATE),
                    current.get(Traces.BAGGAGE));
            if (context != null) {
                event = request.withTraceContext(context);
            }
        }
        write(ENGINE, event.partitionKey(), event.getClass().getSimpleName(), serialise(DomainEvent.class, event));
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
        var message = new OutboxMessageEntity();
        message.binding = binding;
        message.messageKey = key;
        message.eventType = type;
        message.payload = payload;
        message.createdAt = clock.instant();
        var trace = traces.current();
        message.traceparent = trace.get(Traces.TRACEPARENT);
        message.tracestate = trace.get(Traces.TRACESTATE);
        repository.save(message);
    }
}
