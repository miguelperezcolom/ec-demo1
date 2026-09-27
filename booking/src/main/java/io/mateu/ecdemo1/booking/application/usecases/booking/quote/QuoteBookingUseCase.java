package io.mateu.ecdemo1.booking.application.usecases.booking.quote;

import io.mateu.ecdemo1.booking.application.usecases.booking.BookingRequest;
import io.mateu.ecdemo1.booking.application.usecases.booking.BookingTermsFactory;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Prices a booking without making it: for a channel that has to tell the customer the price before
 * the customer says yes — the hotel's front office with a walk-in. The request is checked as creating
 * it would check it, but it needs no holder yet, and a room is refused if its people do not fit.
 */
@Service
@RequiredArgsConstructor
public class QuoteBookingUseCase {

    final CrsCatalog catalog;
    final BookingTermsFactory termsFactory;

    public Quote handle(String hotelCode, BookingRequest request) {
        var hotel = catalog.hotel(hotelCode);
        return termsFactory.quote(hotel, request);
    }
}
