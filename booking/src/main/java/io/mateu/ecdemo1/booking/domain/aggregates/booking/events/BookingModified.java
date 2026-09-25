package io.mateu.ecdemo1.booking.domain.aggregates.booking.events;

import com.fasterxml.jackson.annotation.JsonTypeName;

import java.time.Instant;

/**
 * The booking changed and is still alive. {@code change} says what kind of change it was, for
 * whoever reads the event stream; a consumer should not need it to act.
 */
@JsonTypeName("booking-modified")
public record BookingModified(String eventId, String bookingId, String hotelCode, long version,
                              Instant occurredAt, BookingChange change) implements BookingEvent {
}
