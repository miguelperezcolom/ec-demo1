package io.mateu.ecdemo1.booking.application.out.query.dto;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingStatus;

import java.time.LocalDate;
import java.util.Set;

/**
 * What narrows the bookings listing besides the free text. Every part is optional: a null (or an
 * empty set) does not filter, and each date bound is inclusive. {@code ids} is a concrete set of
 * bookings — the listing's {@code ?ids=4MBZS7,JXD3G6}, how the assistant shows the bookings it found.
 */
public record BookingCriteria(String hotelCode,
                              Set<BookingStatus> statuses,
                              LocalDate arrivalFrom,
                              LocalDate arrivalTo,
                              LocalDate departureFrom,
                              LocalDate departureTo,
                              Set<String> ids) {

    public BookingCriteria(String hotelCode, Set<BookingStatus> statuses, LocalDate arrivalFrom,
                           LocalDate arrivalTo, LocalDate departureFrom, LocalDate departureTo) {
        this(hotelCode, statuses, arrivalFrom, arrivalTo, departureFrom, departureTo, null);
    }
}
