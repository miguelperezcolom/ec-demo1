package io.mateu.ecdemo1.booking.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingChange;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingCreated;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingModified;
import io.mateu.workflow.ddd.DomainEvent;
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

    @Test
    void carriesATypeDiscriminatorAndNoBookingData() throws Exception {
        var json = mapper.readTree(mapper.writerFor(DomainEvent.class).writeValueAsString(
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
        var json = mapper.readTree(mapper.writerFor(DomainEvent.class).writeValueAsString(
                new BookingModified("E2", "B1", "PMI01", 2, Instant.now(), BookingChange.Confirmed)));

        assertThat(json.get("type").asText()).isEqualTo("booking-modified");
        assertThat(json.get("change").asText()).isEqualTo("Confirmed");
    }
}
