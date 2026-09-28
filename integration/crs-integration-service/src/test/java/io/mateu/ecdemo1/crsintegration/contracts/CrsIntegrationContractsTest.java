package io.mateu.ecdemo1.crsintegration.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.contracts.testing.Contracts;
import io.mateu.ecdemo1.contracts.testing.SchemaFiles;
import io.mateu.ecdemo1.contracts.testing.TopicSpec;
import io.mateu.ecdemo1.crsintegration.commands.SystemCommands;
import io.mateu.ecdemo1.crsintegration.config.StreamFunctions;
import io.mateu.ecdemo1.crsintegration.config.TolerantReader;
import io.mateu.ecdemo1.crsintegration.in.CrsEventHandler;
import io.mateu.ecdemo1.crsintegration.noshow.NoShowReports;
import io.mateu.ecdemo1.crsintegration.outbox.Outbox;
import io.mateu.ecdemo1.crsintegration.router.Integrations;
import io.mateu.ecdemo1.crsintegration.router.ProcessRouter;
import io.mateu.ecdemo1.crsintegration.source.BookingView;
import io.mateu.ecdemo1.crsintegration.source.CrsSource;
import io.mateu.ecdemo1.integration.model.command.ProjectReservation;
import io.mateu.ecdemo1.integration.model.command.ReportNoShow;
import io.mateu.ecdemo1.integration.model.events.IntegrationEvent;
import io.mateu.ecdemo1.integration.model.events.PartnerChanged;
import io.mateu.ecdemo1.integration.model.events.ReservationCancelled;
import io.mateu.ecdemo1.integration.model.events.ReservationCreated;
import io.mateu.ecdemo1.integration.model.events.ReservationModified;
import io.mateu.ecdemo1.messaging.Inbox;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * crs-integration's topics. It owns integration-events — its own business events, which only it
 * reads — so that schema is generated here ({@code -Dcontracts.write=true} writes it). What it
 * publishes (integration-events, and the commands to the CRS and the master of partners) is checked
 * as its outbox really writes it; what it reads (crs-bookings, partners, integration-events,
 * customers, projection-requests, no-show-reports) by feeding every example of the topic's schema
 * through the consumer the binding runs.
 */
class CrsIntegrationContractsTest {

    static final Instant AT = Instant.parse("2026-11-12T09:30:00Z");

    static ObjectMapper bootMapper() {
        try (var context = new AnnotationConfigApplicationContext(JacksonAutoConfiguration.class)) {
            return context.getBean(ObjectMapper.class);
        }
    }

    final ObjectMapper boot = bootMapper();
    final io.mateu.ecdemo1.messaging.Outbox shared = mock(io.mateu.ecdemo1.messaging.Outbox.class);
    final Outbox outbox = new Outbox(shared, null, boot);

    final CrsEventHandler crsEvents = mock(CrsEventHandler.class);
    final ProcessRouter router = mock(ProcessRouter.class);
    final Integrations integrations = mock(Integrations.class);
    final NoShowReports noShows = mock(NoShowReports.class);
    final StreamFunctions functions = new StreamFunctions(crsEvents, router, integrations, new TolerantReader(boot), noShows);

    static Message<byte[]> message(String json) {
        return MessageBuilder.withPayload(json.getBytes()).build();
    }

    /** What the outbox appends to a binding, as the relay will publish it. */
    List<String> written(String binding, int count) {
        var payloads = ArgumentCaptor.forClass(String.class);
        verify(shared, times(count)).append(eq(binding), anyString(), anyString(), payloads.capture(), any());
        return payloads.getAllValues();
    }

    List<String> integrationEvents() {
        var events = List.<IntegrationEvent>of(
                new ReservationCreated("E-1", AT, "MRU01", "12E45", 1),
                new ReservationModified("E-2", AT, "MRU01", "12E45", 2),
                new ReservationCancelled("E-3", AT, "MRU01", "12E45", 3),
                new PartnerChanged("E-4", AT, "NORDTRAVEL", 4));
        events.forEach(outbox::append);
        return written(Outbox.INTEGRATION_EVENTS, events.size());
    }

    @Test
    void theSchemaOfIntegrationEventsIsWhatItsEventsAre() {
        var spec = TopicSpec.topic("integration-events")
                .describedAs("The integration's business events — a reservation created, changed or cancelled "
                        + "in the CRS, a partner changed — in its own terms, each routed to the process it calls "
                        + "for. Internal: crs-integration writes them (outbox) and reads them itself.")
                .ownedBy("crs-integration-service").keyedBy("hotelCode/locator, or partner/<partnerCode>")
                .producedBy("crs-integration-service").consumedBy("crs-integration-service")
                .messages(IntegrationEvent.class);
        integrationEvents().forEach(spec::example);
        SchemaFiles.publish(spec);
    }

    @Test
    void theIntegrationEventsTheOutboxWritesAreWhatTheSchemaSays() {
        var schema = Contracts.topic("integration-events");
        integrationEvents().forEach(schema::assertValid);
    }

    @Test
    void theCommandsToTheCrsAndTheMasterOfPartnersAreWhatTheirOwnersTake() {
        outbox.appendToCrs(new SystemCommands.AnnotatePmsReference("TE-1", "12E45", "123456"));
        outbox.appendToPartners(new SystemCommands.RecordPmsProfile("TE-2", "NORDTRAVEL", "16120699", "Agent"));

        written(Outbox.BOOKING_COMMANDS, 1).forEach(Contracts.topic("booking-commands")::assertValid);
        written(Outbox.PARTNER_COMMANDS, 1).forEach(Contracts.topic("partner-commands")::assertValid);
    }

    @Test
    void everyExampleOfCrsBookingsAndPartnersIsHandled() {
        var examples = new java.util.ArrayList<String>();
        examples.addAll(Contracts.topic("crs-bookings").examples());
        examples.addAll(Contracts.topic("partners").examples());
        var consumer = functions.consumeCrsEvents();
        examples.forEach(json -> consumer.accept(message(json)));

        var read = ArgumentCaptor.forClass(JsonNode.class);
        verify(crsEvents, times(examples.size())).handle(read.capture());
        assertThat(read.getAllValues()).extracting(e -> e.path("type").asText())
                .contains("booking-created", "booking-modified", "booking-cancelled", "partner-changed");
    }

    /**
     * Past the parse: the handler translates every example of crs-bookings and partners into a
     * business event, and what it writes is what integration-events' schema says.
     */
    @Test
    void everyExampleOfCrsBookingsAndPartnersBecomesAnIntegrationEventOfItsSchema() {
        var inbox = mock(Inbox.class);
        when(inbox.firstTime(anyString(), anyString())).thenReturn(true);
        var crs = mock(CrsSource.class);
        var booking = mock(BookingView.class);
        when(booking.hotelCode()).thenReturn("MRU01");
        when(crs.booking(anyString())).thenReturn(Optional.of(booking));
        var handler = new CrsEventHandler(inbox, outbox, crs);
        var reader = new TolerantReader(boot);

        var examples = new java.util.ArrayList<String>();
        examples.addAll(Contracts.topic("crs-bookings").examples());
        examples.addAll(Contracts.topic("partners").examples());
        for (var json : examples) {
            try {
                handler.handle(reader.mapper().readTree(json));
            } catch (java.io.IOException e) {
                throw new AssertionError(e);
            }
        }

        var schema = Contracts.topic("integration-events");
        var events = written(Outbox.INTEGRATION_EVENTS, examples.size());
        events.forEach(schema::assertValid);
        assertThat(events).anySatisfy(e -> assertThat(e).contains("\"type\":\"partner-changed\""));
    }

    @Test
    void everyExampleOfIntegrationEventsIsRouted() {
        var examples = Contracts.topic("integration-events").examples();
        var consumer = functions.routeIntegrationEvents();
        examples.forEach(json -> consumer.accept(message(json)));

        var read = ArgumentCaptor.forClass(IntegrationEvent.class);
        verify(router, times(examples.size())).route(read.capture());
        assertThat(read.getAllValues()).allSatisfy(e -> {
            assertThat(e.eventId()).isNotBlank();
            assertThat(e.occurredAt()).isNotNull();
            assertThat(e.key()).doesNotContain("null");
        });
    }

    @Test
    void everyExampleOfCustomersProjectsTheReservationsItIsOn() {
        when(integrations.integrated("MRU01")).thenReturn(true);
        var examples = Contracts.topic("customers").examples();
        var consumer = functions.consumeCustomerEvents();
        examples.forEach(json -> consumer.accept(message(json)));

        // Every example carries data that changed (or a merge), and reservations of MRU01.
        verify(router, atLeastOnce()).project(eq("MRU01"), eq("12E45"), anyString());
        verify(integrations, atLeastOnce()).integrated("MRU01");
    }

    @Test
    void everyExampleOfProjectionRequestsStartsItsProjection() {
        var examples = Contracts.topic("projection-requests").examples();
        var consumer = functions.consumeProjectionRequests();
        examples.forEach(json -> consumer.accept(message(json)));

        for (var json : examples) {
            ProjectReservation request;
            try {
                request = boot.readValue(json, ProjectReservation.class);
            } catch (java.io.IOException e) {
                throw new AssertionError(e);
            }
            verify(router).project(request.hotelCode(), request.locator(), request.origin());
        }
    }

    @Test
    void everyExampleOfNoShowReportsIsTaken() {
        var examples = Contracts.topic("no-show-reports").examples();
        var consumer = functions.consumeNoShowReports();
        examples.forEach(json -> consumer.accept(message(json)));

        var read = ArgumentCaptor.forClass(ReportNoShow.class);
        verify(noShows, times(examples.size())).handle(read.capture());
        assertThat(read.getAllValues()).allSatisfy(r -> {
            assertThat(r.commandId()).isNotBlank();
            assertThat(r.hotelCode()).isNotBlank();
            assertThat(r.locator()).isNotBlank();
        });
    }
}
