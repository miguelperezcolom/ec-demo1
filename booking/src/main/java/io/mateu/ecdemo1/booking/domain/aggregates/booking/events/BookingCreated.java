package io.mateu.ecdemo1.booking.domain.aggregates.booking.events;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.time.Instant;

@JsonTypeName("booking-created")
public record BookingCreated(String eventId, String bookingId, String hotelCode, long version,
                             Instant occurredAt) implements BookingEvent {
}
