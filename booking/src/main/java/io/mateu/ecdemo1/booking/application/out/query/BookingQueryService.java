package io.mateu.ecdemo1.booking.application.out.query;

import io.mateu.ecdemo1.booking.application.out.query.dto.BookingDto;
import io.mateu.ecdemo1.booking.application.out.query.dto.BookingRow;

import java.util.List;

public interface BookingQueryService extends QueryService<BookingDto, BookingRow, String> {

    /** Most recent first; {@code text} matches the id, the holder's name or the hotel. */
    List<BookingDto> list(String text, int page, int size);
}
