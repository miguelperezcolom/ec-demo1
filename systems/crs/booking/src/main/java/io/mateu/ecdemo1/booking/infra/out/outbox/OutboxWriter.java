package io.mateu.ecdemo1.booking.infra.out.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import io.mateu.ecdemo1.booking.application.out.outbox.Outbox;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingCancelled;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingCreated;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingModified;
import io.mateu.ecdemo1.messaging.engine.EngineOutbox;
import io.mateu.workflow.ddd.DomainEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The CRS's outbox port, on the shared outbox ({@link io.mateu.ecdemo1.messaging.Outbox}): the table,
 * the relay and the trace context carried to the record are the library's; what is here is the CRS's
 * own — which binding each destination is, and how its events are written on the wire.
 */
@Component
public class OutboxWriter implements Outbox {

    /**
     * The name each of the CRS's events goes by on the wire — the {@code type} its consumers
     * dispatch on. It is the contract with them, and it lives here, with the serialisation, rather
     * than as annotations on the domain's events.
     */
    static final List<NamedType> EVENT_TYPES = List.of(
            new NamedType(BookingCreated.class, "booking-created"),
            new NamedType(BookingModified.class, "booking-modified"),
            new NamedType(BookingCancelled.class, "booking-cancelled"));

    final io.mateu.ecdemo1.messaging.Outbox outbox;
    final EngineOutbox engine;
    final ObjectWriter writer;
    final String bookingEventsBinding;

    public OutboxWriter(io.mateu.ecdemo1.messaging.Outbox outbox, EngineOutbox engine, ObjectMapper objectMapper,
                        @Value("${outbox.booking-events-binding:bookingEvents}") String bookingEventsBinding) {
        this.outbox = outbox;
        this.engine = engine;
        this.writer = writerFor(objectMapper);
        this.bookingEventsBinding = bookingEventsBinding;
    }

    /**
     * Writes an event as its declared supertype, so the payload carries the {@code type}
     * discriminator: the engine's names for its own requests, {@link #EVENT_TYPES} for the CRS's.
     * A copy of the application's mapper, so its dates and modules are the same and the names are
     * registered nowhere else.
     */
    static ObjectWriter writerFor(ObjectMapper objectMapper) {
        var mapper = objectMapper.copy();
        mapper.registerSubtypes(EVENT_TYPES.toArray(NamedType[]::new));
        return mapper.writerFor(DomainEvent.class);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(Destination destination, DomainEvent event) {
        switch (destination) {
            // A process the CRS asks the engine for joins the trace it was asked in (EngineOutbox).
            case Engine -> engine.append(event, writer);
            case BookingEvents -> {
                String payload;
                try {
                    payload = writer.writeValueAsString(event);
                } catch (JsonProcessingException e) {
                    throw new IllegalStateException("Cannot serialise " + event, e);
                }
                outbox.append(bookingEventsBinding, event.partitionKey(), event.getClass().getSimpleName(), payload, null);
            }
        }
    }
}
