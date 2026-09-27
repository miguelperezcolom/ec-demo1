package io.mateu.ecdemo1.mdm.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.customer.CustomerEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * What must leave only if the decision that produced it was saved: the MDM's events about a
 * customer, on the {@code customers} topic, keyed by the customer.
 *
 * <p>This service's messages, in its words; the outbox itself — the table, the relay, the trace
 * context carried to the record — is the shared one ({@link io.mateu.ecdemo1.messaging.Outbox}).
 */
@Component
@RequiredArgsConstructor
public class Outbox {

    public static final String CUSTOMERS = "customers";
    public static final String NOTIFICATIONS = "notifications";
    public static final String RESOLUTIONS = "resolutions";

    final io.mateu.ecdemo1.messaging.Outbox outbox;
    final ObjectMapper objectMapper;
    final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(CustomerEvent event) {
        write(CUSTOMERS, event.customerId(), event.getClass().getSimpleName(), serialise(CustomerEvent.class, event));
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
        outbox.append(binding, key, type, payload, null);
    }

    String serialise(Class<?> as, Object value) {
        try {
            return objectMapper.writerFor(as).writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + value, e);
        }
    }
}
