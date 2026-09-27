package io.mateu.ecdemo1.booking.application.usecases.booking.update;

import io.mateu.ecdemo1.booking.application.out.repository.BookingRepository;
import io.mateu.ecdemo1.booking.application.usecases.booking.BookingTermsFactory;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.NoSuchElementException;

/** Replaces the booking's terms as a whole and prices it again, as a CRS modification does. */
@Service
@RequiredArgsConstructor
public class UpdateBookingUseCase {

    final BookingRepository repository;
    final BookingTermsFactory termsFactory;
    final Clock clock;

    @Transactional
    public void handle(UpdateBookingCommand command) {
        var booking = repository.findByIdForUpdate(new BookingId(command.id()))
                .orElseThrow(() -> new NoSuchElementException("Booking not found: " + command.id()));
        booking.update(termsFactory.terms(booking.getHotelCode(), command.booking()), clock.instant());
        repository.save(booking);
    }

}
