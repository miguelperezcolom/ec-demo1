package io.mateu.ecdemo1.booking.application.usecases.booking.create;

import io.mateu.ecdemo1.booking.application.usecases.booking.BookingRequest;
import io.mateu.ecdemo1.booking.application.usecases.booking.PaymentRequest;

import java.math.BigDecimal;
import java.util.List;

/**
 * @param expectedTotal what the channel told the customer the booking costs, from a quote: if the CRS
 *                      prices it differently now, the booking is refused rather than made at a price
 *                      nobody agreed to. Null, the CRS's price is taken as it comes.
 * @param payments      what was collected as the booking was made. They are part of making it — one
 *                      change, one version, one event — not payments registered after it. Null is
 *                      none.
 */
public record CreateBookingCommand(String hotelCode, BookingRequest booking, BigDecimal expectedTotal,
                                   List<PaymentRequest> payments) {

    public CreateBookingCommand {
        payments = payments != null ? List.copyOf(payments) : List.of();
    }

    public CreateBookingCommand(String hotelCode, BookingRequest booking, BigDecimal expectedTotal) {
        this(hotelCode, booking, expectedTotal, List.of());
    }

    public CreateBookingCommand(String hotelCode, BookingRequest booking) {
        this(hotelCode, booking, null, List.of());
    }
}
