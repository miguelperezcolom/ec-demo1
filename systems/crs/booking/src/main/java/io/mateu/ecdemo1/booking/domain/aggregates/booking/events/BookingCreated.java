package io.mateu.ecdemo1.booking.domain.aggregates.booking.events;

import java.time.Instant;

public record BookingCreated(String eventId, String bookingId, String hotelCode, long version,
                             Instant occurredAt) implements BookingEvent {
}
