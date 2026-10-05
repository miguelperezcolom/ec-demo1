package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.ecdemo1.booking.application.out.query.dto.BookingDto;
import io.mateu.uidl.annotations.Line;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;

import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/**
 * A booking as a line of the listing. The screen's own: the query service answers bookings, not rows.
 *
 * <p>Two lines per booking: who, where and when are the columns; what it costs, its status, its
 * version and its PMS reservation go under them ({@code @Line(2)}), so the listing fits without a
 * sideways scroll.
 */
public record BookingRow(String id,
                         String hotel,
                         String holder,
                         String arrival,
                         String departure,
                         @Line(2) String total,
                         @Line(2) Status status,
                         @Line(2) long version,
                         @Line(2) String pmsReservationId) {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** A person reads a hotel by its name: {@code hotelName} answers it for a code. */
    static BookingRow of(BookingDto booking, Function<String, String> hotelName) {
        return new BookingRow(
                booking.id(),
                hotelName.apply(booking.hotelCode()),
                booking.holder().fullName(),
                booking.arrival().format(DATE),
                booking.departure().format(DATE),
                booking.totalAmount().toPlainString() + " " + booking.currency(),
                status(booking),
                booking.version(),
                booking.pmsReference() != null ? booking.pmsReference().reservationId() : null);
    }

    static Status status(BookingDto booking) {
        return new Status(switch (booking.status()) {
            case Pending -> StatusType.INFO;
            case Confirmed -> StatusType.SUCCESS;
            case Cancelled -> StatusType.DANGER;
        }, booking.status().name());
    }
}
