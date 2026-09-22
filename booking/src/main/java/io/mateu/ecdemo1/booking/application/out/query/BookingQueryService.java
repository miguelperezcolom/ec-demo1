package io.mateu.ecdemo1.booking.application.out.query;

import io.mateu.ecdemo1.booking.application.out.query.dto.BookingDto;
import io.mateu.ecdemo1.booking.application.out.query.dto.BookingRow;

import java.time.LocalDate;
import java.util.List;

public interface BookingQueryService extends QueryService<BookingDto, BookingRow, String> {

    /** Most recent first; {@code text} matches the id, the holder's name or the hotel. */
    List<BookingDto> list(String text, int page, int size);

    /**
     * A hotel's bookings arriving on or after {@code from}, not cancelled, by arrival and then id,
     * after the given position ({@code afterArrival} and {@code afterId}, both null for the first page).
     */
    List<BookingDto> future(String hotelCode, LocalDate from, LocalDate afterArrival, String afterId, int limit);
}
