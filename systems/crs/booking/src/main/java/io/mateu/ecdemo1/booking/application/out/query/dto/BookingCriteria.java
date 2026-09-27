package io.mateu.ecdemo1.booking.application.out.query.dto;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingStatus;

import java.time.LocalDate;
import java.util.Set;

/**
 * What narrows the bookings listing besides the free text. Every part is optional: a null (or an
 * empty set) does not filter, and each date bound is inclusive.
 */
public record BookingCriteria(String hotelCode,
                              Set<BookingStatus> statuses,
                              LocalDate arrivalFrom,
                              LocalDate arrivalTo,
                              LocalDate departureFrom,
                              LocalDate departureTo) {
}
