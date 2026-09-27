package io.mateu.ecdemo1.booking.application.usecases.booking.pmsreference;

import io.mateu.ecdemo1.booking.application.out.repository.BookingRepository;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.NoSuchElementException;

/**
 * Written by the integration once the booking is in the PMS, so an operator of the CRS can see
 * where it landed. Records no event and does not version the booking.
 */
@Service
@RequiredArgsConstructor
public class AnnotatePmsReferenceUseCase {

    final BookingRepository repository;
    final Clock clock;

    @Transactional
    public void handle(AnnotatePmsReferenceCommand command) {
        var booking = repository.findByIdForUpdate(new BookingId(command.id()))
                .orElseThrow(() -> new NoSuchElementException("Booking not found: " + command.id()));
        booking.annotatePmsReference(command.reservationId(), clock.instant());
        repository.save(booking);
    }

}
