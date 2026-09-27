package io.mateu.ecdemo1.booking.application.out.repository;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.Booking;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingId;

import java.util.Optional;

/**
 * Saving a booking also puts the events it recorded in the outbox, in the same transaction.
 */
public interface BookingRepository extends Repository<Booking, BookingId> {

    /**
     * Loads the booking and holds it until the transaction ends. Every change goes through here,
     * so two changes to one booking cannot interleave and hand out the same version.
     */
    Optional<Booking> findByIdForUpdate(BookingId id);

    /**
     * The booking a channel already made under its own reference in a hotel, if any: a channel that
     * sends the same booking again — a front office retrying after a timeout — gets the same one back.
     */
    Optional<BookingId> findByChannelReference(String hotelCode, String channelCode, String externalReference);
}
