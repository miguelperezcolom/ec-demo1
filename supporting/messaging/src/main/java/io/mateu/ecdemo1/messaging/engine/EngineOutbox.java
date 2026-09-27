package io.mateu.ecdemo1.messaging.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.mateu.ecdemo1.messaging.Outbox;
import io.mateu.ecdemo1.messaging.TraceContexts;
import io.mateu.workflow.ddd.DomainEvent;
import io.mateu.workflow.dtos.TraceContext;
import io.mateu.workflow.dtos.events.integration.ProcessCreationRequested;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Requests to the workflow engine — a process to start ({@link ProcessCreationRequested}), a message
 * for one that waits — through the outbox, on the binding that reaches the engine's {@code upstream}
 * topic. A process started here joins the trace it was asked for in: the current context goes in the
 * request's {@code traceContext}, which the engine keeps with the process and hands on to every task
 * it dispatches for it.
 */
public class EngineOutbox {

    final Outbox outbox;
    final TraceContexts traces;
    final ObjectProvider<ObjectMapper> objectMapper;
    final String destination;

    public EngineOutbox(Outbox outbox, TraceContexts traces, ObjectProvider<ObjectMapper> objectMapper,
                        String destination) {
        this.outbox = outbox;
        this.traces = traces;
        this.objectMapper = objectMapper;
        this.destination = destination;
    }

    /** The binding the requests go through. */
    public String destination() {
        return destination;
    }

    /** Appends a request, written as a {@link DomainEvent} (its {@code type} discriminator included). */
    public void append(DomainEvent event) {
        append(event, objectMapper.getIfAvailable(ObjectMapper::new).writerFor(DomainEvent.class));
    }

    /** Appends a request, written with the given writer. */
    public void append(DomainEvent event, ObjectWriter writer) {
        event = withTraceContext(event);
        String payload;
        try {
            payload = writer.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + event, e);
        }
        outbox.append(destination, event.partitionKey(), event.getClass().getSimpleName(), payload, null);
    }

    /** The request, joining the current trace when it is a process creation that names none. */
    public DomainEvent withTraceContext(DomainEvent event) {
        if (event instanceof ProcessCreationRequested request && request.traceContext() == null) {
            var current = traces.current();
            var context = TraceContext.of(current.get(TraceContexts.TRACEPARENT), current.get(TraceContexts.TRACESTATE),
                    current.get(TraceContexts.BAGGAGE));
            if (context != null) {
                return request.withTraceContext(context);
            }
        }
        return event;
    }
}
