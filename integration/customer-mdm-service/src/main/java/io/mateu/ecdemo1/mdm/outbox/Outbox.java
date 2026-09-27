package io.mateu.ecdemo1.mdm.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.customer.CustomerEvent;
import io.mateu.ecdemo1.mdm.tracing.Traces;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * What must leave only if the decision that produced it was saved: the MDM's events about a
 * customer, on the {@code customers} topic, keyed by the customer.
 */
@Component
@RequiredArgsConstructor
public class Outbox {

    public static final String CUSTOMERS = "customers";
    public static final String NOTIFICATIONS = "notifications";
    public static final String RESOLUTIONS = "resolutions";

    final OutboxMessageRepository repository;
    final ObjectMapper objectMapper;
    final Clock clock;
    final Traces traces;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(CustomerEvent event) {
        var message = new OutboxMessageEntity();
        message.binding = CUSTOMERS;
        message.messageKey = event.customerId();
        message.eventType = event.getClass().getSimpleName();
        try {
            message.payload = objectMapper.writerFor(CustomerEvent.class).writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + event, e);
        }
        message.createdAt = clock.instant();
        var trace = traces.current();
        message.traceparent = trace.get(Traces.TRACEPARENT);
        message.tracestate = trace.get(Traces.TRACESTATE);
        repository.save(message);
    }

    /** A person to be told something: the communication service decides who, and how. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendNotification(io.mateu.ecdemo1.integration.model.notification.NotificationRequested n) {
        write(NOTIFICATIONS, n.dedupKey(), "NotificationRequested",
                serialise(io.mateu.ecdemo1.integration.model.notification.NotificationRequested.class, n));
    }

    /** What the notifications about this subject asked for is no longer waiting: they close in every inbox. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendResolution(String subject, String resolvedBy) {
        write(RESOLUTIONS, subject, "NotificationResolved",
                serialise(io.mateu.ecdemo1.integration.model.notification.NotificationResolved.class,
                        new io.mateu.ecdemo1.integration.model.notification.NotificationResolved(subject, resolvedBy, clock.instant())));
    }

    void write(String binding, String key, String type, String payload) {
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

    String serialise(Class<?> as, Object value) {
        try {
            return objectMapper.writerFor(as).writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + value, e);
        }
    }
}
