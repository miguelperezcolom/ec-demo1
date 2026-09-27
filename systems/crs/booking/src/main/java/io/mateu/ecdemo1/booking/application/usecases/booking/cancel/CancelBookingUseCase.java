package io.mateu.ecdemo1.booking.application.usecases.booking.cancel;

import io.mateu.ecdemo1.booking.application.out.repository.BookingRepository;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingId;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class CancelBookingUseCase {

    final BookingRepository repository;
    final CrsCatalog catalog;
    final Clock clock;

    @Transactional
    public void handle(CancelBookingCommand command) {
        var booking = repository.findByIdForUpdate(new BookingId(command.id()))
                .orElseThrow(() -> new NoSuchElementException("Booking not found: " + command.id()));
        // A hotel may cancel with reasons of its own: checked against the booking's hotel.
        var reason = catalog.cancellationReason(booking.getHotelCode(), command.reasonCode());
        booking.cancel(reason.code(), clock.instant());
        repository.save(booking);
    }

}
