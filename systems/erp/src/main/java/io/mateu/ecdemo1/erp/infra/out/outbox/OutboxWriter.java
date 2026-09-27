package io.mateu.ecdemo1.erp.infra.out.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.workflow.ddd.DomainEvent;
import io.mateu.ecdemo1.erp.tracing.Traces;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** Writes events to the outbox in the caller's transaction; {@link OutboxRelay} publishes them. */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    final OutboxMessageRepository repository;
    final OutboxProperties properties;
    final ObjectMapper objectMapper;
    final Clock clock;
    final Traces traces;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(DomainEvent event) {
        var message = new OutboxMessageEntity();
        message.binding = properties.binding();
        message.messageKey = event.partitionKey();
        message.eventType = event.getClass().getSimpleName();
        try {
            message.payload = objectMapper.writerFor(DomainEvent.class).writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + event, e);
        }
        message.createdAt = clock.instant();
        var trace = traces.current();
        message.traceparent = trace.get(Traces.TRACEPARENT);
        message.tracestate = trace.get(Traces.TRACESTATE);
        repository.save(message);
    }
}
