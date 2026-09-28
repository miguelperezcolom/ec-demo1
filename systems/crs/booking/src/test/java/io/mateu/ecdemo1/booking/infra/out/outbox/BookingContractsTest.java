package io.mateu.ecdemo1.booking.infra.out.outbox;

import io.mateu.ecdemo1.booking.application.usecases.commands.BookingCommands;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingCancelled;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingChange;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingCreated;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingModified;
import io.mateu.ecdemo1.booking.application.out.outbox.Outbox.Destination;
import io.mateu.ecdemo1.booking.infra.in.async.BookingCommandsConsumer;
import io.mateu.ecdemo1.contracts.testing.Contracts;
import io.mateu.ecdemo1.contracts.testing.SchemaFiles;
import io.mateu.ecdemo1.contracts.testing.TopicSpec;
import io.mateu.ecdemo1.messaging.Outbox;
import io.mateu.workflow.ddd.DomainEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The CRS's topics (here, beside the outbox writer whose names for the events are the contract). It owns two — crs-bookings, the events it publishes, and booking-commands, what
 * it takes — so their schemas are generated here from its records ({@code -Dcontracts.write=true}
 * writes them to contracts/schemas). The events are checked as the outbox really writes them, and
 * every example of booking-commands is read the way the consumer reads the topic.
 */
class BookingContractsTest {

    static final Instant AT = Instant.parse("2026-11-12T09:30:00Z");

    /** The application's mapper, as Spring Boot's auto-configuration builds it: nothing here configures another. */
    static com.fasterxml.jackson.databind.ObjectMapper bootMapper() {
        try (var context = new AnnotationConfigApplicationContext(JacksonAutoConfiguration.class)) {
            return context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class);
        }
    }

    final com.fasterxml.jackson.databind.ObjectMapper boot = bootMapper();

    /** The payloads the outbox writer really appends for these events. */
    List<String> published(DomainEvent... events) {
        var outbox = mock(Outbox.class);
        var writer = new OutboxWriter(outbox, null, boot, "bookingEvents");
        for (var event : events) {
            writer.append(Destination.BookingEvents, event);
        }
        var payloads = ArgumentCaptor.forClass(String.class);
        verify(outbox, times(events.length)).append(eq("bookingEvents"), anyString(), anyString(), payloads.capture(), any());
        return payloads.getAllValues();
    }

    List<String> events() {
        return published(new BookingCreated("E-1", "12E45", "MRU01", 1, AT),
                new BookingModified("E-2", "12E45", "MRU01", 2, AT, BookingChange.Confirmed),
                new BookingCancelled("E-3", "12E45", "MRU01", 3, AT, "NOS"));
    }

    @Test
    void theSchemaOfCrsBookingsIsWhatItsEventsAre() {
        var spec = TopicSpec.topic("crs-bookings")
                .describedAs("A booking created, changed or cancelled in the CRS. Thin: whoever needs the "
                        + "booking reads it (its version tells a reader whether what it holds is older).")
                .ownedBy("booking").keyedBy("bookingId (the CRS locator)")
                .producedBy("booking").consumedBy("crs-integration-service");
        OutboxWriter.EVENT_TYPES.forEach(type -> spec.variant("type", type.getName(), type.getType()));
        events().forEach(spec::example);
        SchemaFiles.publish(spec);
    }

    @Test
    void theEventsTheOutboxWritesAreWhatTheSchemaSays() {
        var schema = Contracts.topic("crs-bookings");
        events().forEach(schema::assertValid);
    }

    @Test
    void theSchemaOfBookingCommandsIsWhatTheCrsTakes() {
        SchemaFiles.publish(TopicSpec.topic("booking-commands")
                .describedAs("What other services ask of the CRS without waiting: today, the integration "
                        + "writing back where a booking landed in the PMS. Taken once each (inbox).")
                .ownedBy("booking").keyedBy("bookingId")
                .producedBy("crs-integration-service").consumedBy("booking")
                .messages(BookingCommands.Command.class)
                .example(new BookingCommands.AnnotatePmsReference("CMD-1", "12E45", "123456")));
    }

    @Test
    void everyExampleOfBookingCommandsIsTakenByTheConsumer() {
        var commands = mock(BookingCommands.class);
        var consumer = new BookingCommandsConsumer(commands, boot).consumeBookingCommands();

        var examples = Contracts.topic("booking-commands").examples();
        examples.forEach(json -> consumer.accept(MessageBuilder.withPayload(json.getBytes()).build()));

        var taken = ArgumentCaptor.forClass(BookingCommands.Command.class);
        verify(commands, times(examples.size())).handle(taken.capture());
        assertThat(taken.getAllValues()).contains(new BookingCommands.AnnotatePmsReference("CMD-1", "12E45", "123456"));
    }
}
