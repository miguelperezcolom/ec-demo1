package io.mateu.ecdemo1.booking.infra.out.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingChange;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingCreated;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingModified;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingCancelled;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.integration.ProcessCreationRequested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shape on the wire is the contract with whoever consumes the CRS's events, so it is pinned
 * here rather than left to whatever Jackson happens to produce.
 */
class BookingEventJsonTest {

    final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    final ObjectWriter writer = OutboxWriter.writerFor(mapper);

    @Test
    void carriesATypeDiscriminatorAndNoBookingData() throws Exception {
        var json = mapper.readTree(writer.writeValueAsString(
                new BookingCreated("E1", "B1", "PMI01", 1, Instant.parse("2026-09-22T10:00:00Z"))));

        assertThat(json.get("type").asText()).isEqualTo("booking-created");
        assertThat(json.get("bookingId").asText()).isEqualTo("B1");
        assertThat(json.get("version").asLong()).isEqualTo(1);
        assertThat(json.get("occurredAt").asText()).isEqualTo("2026-09-22T10:00:00Z");
        assertThat(json.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("type", "eventId", "bookingId", "hotelCode", "version", "occurredAt");
    }

    @Test
    void modificationsNameTheChange() throws Exception {
        var json = mapper.readTree(writer.writeValueAsString(
                new BookingModified("E2", "B1", "PMI01", 2, Instant.now(), BookingChange.Confirmed)));

        assertThat(json.get("type").asText()).isEqualTo("booking-modified");
        assertThat(json.get("change").asText()).isEqualTo("Confirmed");
    }

    @Test
    void cancellationsHaveTheirOwnType() throws Exception {
        var json = mapper.readTree(writer.writeValueAsString(
                new BookingCancelled("E3", "B1", "PMI01", 3, Instant.now(), "CLI")));

        assertThat(json.get("type").asText()).isEqualTo("booking-cancelled");
        assertThat(json.get("reasonCode").asText()).isEqualTo("CLI");
    }

    /** The engine's own requests keep the names the engine gives them. */
    @Test
    void theEnginesRequestsKeepTheirNames() throws Exception {
        var json = mapper.readTree(writer.writeValueAsString(
                new ProcessCreationRequested("registrar-no-show", "K1", java.util.List.of(new Variable("bookingId", "B1")))));

        assertThat(json.get("type").asText()).isEqualTo("process-creation-requested");
    }

    /** The names are the outbox's: the application's own mapper is left as it was. */
    @Test
    void theApplicationsMapperIsNotChanged() throws Exception {
        var json = mapper.readTree(mapper.writerFor(io.mateu.workflow.ddd.DomainEvent.class).writeValueAsString(
                new BookingCreated("E1", "B1", "PMI01", 1, Instant.now())));

        assertThat(json.get("type").asText()).isEqualTo("BookingCreated");
    }
}
