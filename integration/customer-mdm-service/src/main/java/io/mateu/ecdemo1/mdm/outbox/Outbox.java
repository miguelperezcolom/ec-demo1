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
 */
@Component
@RequiredArgsConstructor
public class Outbox {

    public static final String CUSTOMERS = "customers";

    final OutboxMessageRepository repository;
    final ObjectMapper objectMapper;
    final Clock clock;

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
        repository.save(message);
    }
}
