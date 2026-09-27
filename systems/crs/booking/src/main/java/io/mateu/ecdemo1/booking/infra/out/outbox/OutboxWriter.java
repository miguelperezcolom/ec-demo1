package io.mateu.ecdemo1.booking.infra.out.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import io.mateu.ecdemo1.booking.application.out.outbox.Outbox;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingCancelled;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingCreated;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingModified;
import io.mateu.workflow.ddd.DomainEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

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

    final OutboxMessageRepository repository;
    final OutboxProperties properties;
    final ObjectWriter writer;
    final Clock clock;

    public OutboxWriter(OutboxMessageRepository repository, OutboxProperties properties, ObjectMapper objectMapper,
                        Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.writer = writerFor(objectMapper);
        this.clock = clock;
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
        var message = new OutboxMessageEntity();
        message.binding = properties.bindingFor(destination);
        message.messageKey = event.partitionKey();
        message.eventType = event.getClass().getSimpleName();
        try {
            message.payload = writer.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + event, e);
        }
        message.createdAt = clock.instant();
        repository.save(message);
    }
}
