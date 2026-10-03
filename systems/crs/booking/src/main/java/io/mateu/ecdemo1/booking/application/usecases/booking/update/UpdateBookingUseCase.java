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

/**
 * Replaces the booking's terms as a whole and prices it again, as a CRS modification does. Terms
 * that say the same as the booking's are not a modification: nothing is saved, versioned, audited or
 * projected again, and the answer says so — whether the change came from the console, the API or an
 * agent's tool.
 */
@Service
@RequiredArgsConstructor
public class UpdateBookingUseCase {

    final BookingRepository repository;
    final BookingTermsFactory termsFactory;
    final Clock clock;
    final BookingAudit audit;

    /** The demo reset's pause: none in a test that builds the use case by hand. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    io.mateu.ecdemo1.booking.application.usecases.intake.CrsIntake intake;

    void ensureOpen() {
        if (intake != null) {
            intake.ensureOpen();
        }
    }

    /** @return whether the booking changed; false when the terms were the same. */
    @Transactional
    public boolean handle(UpdateBookingCommand command) {
        ensureOpen();
        var booking = repository.findByIdForUpdate(new BookingId(command.id()))
                .orElseThrow(() -> new NoSuchElementException("Booking not found: " + command.id()));
        var terms = termsFactory.terms(booking.getHotelCode(), command.booking());
        if (booking.getTerms().sameAs(terms)) {
            return false;
        }
        audit.run("Booking modified", command.id(), booking.getHotelCode(), null, () -> {
            booking.update(terms, clock.instant());
            return repository.save(booking);
        }, b -> "Modificada");
        return true;
    }

}
