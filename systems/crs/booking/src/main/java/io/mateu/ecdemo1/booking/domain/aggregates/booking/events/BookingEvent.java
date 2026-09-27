package io.mateu.ecdemo1.booking.domain.aggregates.booking.events;

import io.mateu.workflow.ddd.DomainEvent;

import java.time.Instant;

/**
 * Something happened to a booking.
 *
 * <p>Deliberately thin: it says which booking changed and to which version, not what the booking
 * now looks like. Whoever needs the booking reads it again — so a duplicated or late event does
 * no harm, it only causes a read that returns the current state.
 *
 * <p>{@code version} grows by one with every change to the booking and is never reused, which is
 * what lets a consumer tell a stale event from a fresh one. The booking id is the partition key, so
 * the events of one booking keep their order on the topic.
 */
public interface BookingEvent extends DomainEvent {

    String eventId();

    String bookingId();

    String hotelCode();

    long version();

    Instant occurredAt();

    @Override
    default String partitionKey() {
        return bookingId();
    }
}
