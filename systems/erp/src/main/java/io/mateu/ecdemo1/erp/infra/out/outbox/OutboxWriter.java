package io.mateu.ecdemo1.erp.infra.out.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.messaging.Outbox;
import io.mateu.workflow.ddd.DomainEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the master's events to the shared outbox ({@link Outbox}) in the caller's transaction; its
 * relay publishes them through the {@code partnerEvents} binding.
 */
@Component
public class OutboxWriter {

    final Outbox outbox;
    final ObjectMapper objectMapper;
    final String binding;

    public OutboxWriter(Outbox outbox, ObjectMapper objectMapper,
                        @Value("${outbox.binding:partnerEvents}") String binding) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.binding = binding;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(DomainEvent event) {
        String payload;
        try {
            payload = objectMapper.writerFor(DomainEvent.class).writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + event, e);
        }
        outbox.append(binding, event.partitionKey(), event.getClass().getSimpleName(), payload, null);
    }
}
