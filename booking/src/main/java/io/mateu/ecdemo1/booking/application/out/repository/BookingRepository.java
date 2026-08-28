package io.mateu.ecdemo1.booking.application.out.repository;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.Booking;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingId;

public interface BookingRepository extends Repository<Booking, BookingId> {
}
