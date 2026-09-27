package io.mateu.ecdemo1.booking.domain.aggregates.booking.events;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.time.Instant;

@JsonTypeName("booking-cancelled")
public record BookingCancelled(String eventId, String bookingId, String hotelCode, long version,
                               Instant occurredAt, String reasonCode) implements BookingEvent {
}
