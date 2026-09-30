package io.mateu.ecdemo1.booking.application.usecases.booking.update;

import io.mateu.ecdemo1.booking.application.usecases.booking.BookingAudit;
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
    final BookingAudit audit;

    @Transactional
    public void handle(UpdateBookingCommand command) {
        var hotel = repository.findById(new BookingId(command.id())).map(b -> b.getHotelCode()).orElse(null);
        audit.run("Booking modified", command.id(), hotel, null, () -> {
            var booking = repository.findByIdForUpdate(new BookingId(command.id()))
                    .orElseThrow(() -> new NoSuchElementException("Booking not found: " + command.id()));
            booking.update(termsFactory.terms(booking.getHotelCode(), command.booking()), clock.instant());
            return repository.save(booking);
        }, b -> "Modificada");
    }

}
