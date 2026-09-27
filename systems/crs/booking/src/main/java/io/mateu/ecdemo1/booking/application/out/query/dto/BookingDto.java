package io.mateu.ecdemo1.booking.application.out.query.dto;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookedRoom;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingStatus;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Cancellation;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Holder;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Payment;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.PmsReference;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The whole booking, as the CRS holds it now. This is what whoever integrates with the CRS reads
 * again after an event tells it the booking changed — and {@code version} is the version of what
 * it just read.
 */
public record BookingDto(
        String id,
        String hotelCode,
        String currency,
        BookingStatus status,
        long version,
        String channelCode,
        String partnerCode,
        String externalReference,
        LocalDate arrival,
        LocalDate departure,
        int nights,
        Holder holder,
        List<BookedRoom> rooms,
        List<Payment> payments,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        String comments,
        Cancellation cancellation,
        PmsReference pmsReference,
        Instant created,
        Instant updated,
        /** The price as it was booked; totalAmount is what the booking costs now (a no-show's fee). */
        BigDecimal originalAmount
) {
}
