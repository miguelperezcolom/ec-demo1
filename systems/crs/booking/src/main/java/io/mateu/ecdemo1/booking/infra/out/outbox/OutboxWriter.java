package io.mateu.ecdemo1.booking.infra.out.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.booking.application.out.outbox.Outbox;
import io.mateu.workflow.ddd.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Component
@RequiredArgsConstructor
public class OutboxWriter implements Outbox {

    final OutboxMessageRepository repository;
    final OutboxProperties properties;
    final ObjectMapper objectMapper;
    final Clock clock;

    /**
     * Serialised as the event's declared supertype, so the payload carries the {@code type}
     * discriminator every consumer of these topics dispatches on.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(Destination destination, DomainEvent event) {
        var message = new OutboxMessageEntity();
        message.binding = properties.bindingFor(destination);
        message.messageKey = event.partitionKey();
        message.eventType = event.getClass().getSimpleName();
        try {
            message.payload = objectMapper.writerFor(DomainEvent.class).writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + event, e);
        }
        message.createdAt = clock.instant();
        repository.save(message);
    }
}
