package io.mateu.ecdemo1.crsintegration.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.crsintegration.tracing.Traces;
import io.mateu.ecdemo1.integration.model.events.IntegrationEvent;
import io.mateu.workflow.ddd.DomainEvent;
import io.mateu.workflow.dtos.TraceContext;
import io.mateu.workflow.dtos.events.integration.ProcessCreationRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Messages that leave only if the transaction that produced them commits — the same transaction that
 * recorded the event they answer in the inbox, or that a step of the engine did its work in. So an
 * event is either handled and its consequence on its way, or neither. The commands to the systems
 * this adapter fronts go here too: none is sent over HTTP.
 */
@Component
@RequiredArgsConstructor
public class Outbox {

    public static final String INTEGRATION_EVENTS = "integrationEvents";
    public static final String ENGINE = "outboxUpstream";
    public static final String BOOKING_COMMANDS = "bookingCommands";
    public static final String PARTNER_COMMANDS = "partnerCommands";

    final OutboxMessageRepository repository;
    final ObjectMapper objectMapper;
    final Clock clock;
    final Traces traces;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(IntegrationEvent event) {
        write(INTEGRATION_EVENTS, event.key(), event.getClass().getSimpleName(),
                serialise(IntegrationEvent.class, event));
    }

    /**
     * A request to the engine. A process started here joins the trace it was asked for in: the
     * current context goes in the request's {@code traceContext}, which the engine keeps with the
     * process and hands on to every task it dispatches for it.
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

    /** A command for the CRS. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendToCrs(io.mateu.ecdemo1.crsintegration.commands.SystemCommands.AnnotatePmsReference command) {
        write(BOOKING_COMMANDS, command.key(), command.type(), serialise(command.getClass(), command));
    }

    /** A command for the master of partners. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendToPartners(io.mateu.ecdemo1.crsintegration.commands.SystemCommands.RecordPmsProfile command) {
        write(PARTNER_COMMANDS, command.key(), command.type(), serialise(command.getClass(), command));
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
        var trace = traces.current();
        message.traceparent = trace.get(Traces.TRACEPARENT);
        message.tracestate = trace.get(Traces.TRACESTATE);
        repository.save(message);
    }
}
