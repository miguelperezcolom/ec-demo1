package io.mateu.ecdemo1.booking.application.out.outbox;

import io.mateu.workflow.ddd.DomainEvent;

/**
 * Messages that must leave this service if, and only if, the transaction that produced them
 * commits. Appending writes them in the current transaction; a relay publishes them afterwards.
 */
public interface Outbox {

    enum Destination {
        /** What happened to bookings, for whoever integrates with this CRS. */
        BookingEvents,
        /** Requests to the workflow engine. */
        Engine
    }

    void append(Destination destination, DomainEvent event);
}
