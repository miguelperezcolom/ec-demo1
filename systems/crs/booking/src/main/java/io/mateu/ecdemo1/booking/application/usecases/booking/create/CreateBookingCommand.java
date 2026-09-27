package io.mateu.ecdemo1.booking.application.usecases.booking.create;

import io.mateu.ecdemo1.booking.application.usecases.booking.BookingRequest;

import java.math.BigDecimal;

/**
 * @param expectedTotal what the channel told the customer the booking costs, from a quote: if the CRS
 *                      prices it differently now, the booking is refused rather than made at a price
 *                      nobody agreed to. Null, the CRS's price is taken as it comes.
 */
public record CreateBookingCommand(String hotelCode, BookingRequest booking, BigDecimal expectedTotal) {

    public CreateBookingCommand(String hotelCode, BookingRequest booking) {
        this(hotelCode, booking, null);
    }
}
