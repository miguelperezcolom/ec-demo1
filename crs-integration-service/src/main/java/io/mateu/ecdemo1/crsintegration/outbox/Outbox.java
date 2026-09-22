package io.mateu.ecdemo1.crsintegration.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.events.IntegrationEvent;
import io.mateu.workflow.ddd.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Messages that leave only if the transaction that produced them commits — here, always the same
 * transaction that recorded the event they answer in the inbox. So an event is either handled and
 * its consequence on its way, or neither.
 */
@Component
@RequiredArgsConstructor
public class Outbox {

    public static final String INTEGRATION_EVENTS = "integrationEvents";
    public static final String ENGINE = "outboxUpstream";

    final OutboxMessageRepository repository;
    final ObjectMapper objectMapper;
    final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(IntegrationEvent event) {
        write(INTEGRATION_EVENTS, event.key(), event.getClass().getSimpleName(),
                serialise(IntegrationEvent.class, event));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void appendToEngine(DomainEvent event) {
        write(ENGINE, event.partitionKey(), event.getClass().getSimpleName(), serialise(DomainEvent.class, event));
    }

    private String serialise(Class<?> as, Object event) {
        try {
            return objectMapper.writerFor(as).writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + event, e);
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
