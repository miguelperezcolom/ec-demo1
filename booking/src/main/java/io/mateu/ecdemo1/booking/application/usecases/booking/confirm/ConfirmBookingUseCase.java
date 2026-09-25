package io.mateu.ecdemo1.booking.application.usecases.booking.confirm;

import io.mateu.ecdemo1.booking.application.out.repository.BookingRepository;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ConfirmBookingUseCase {

    final BookingRepository repository;
    final Clock clock;

    @Transactional
    public void handle(ConfirmBookingCommand command) {
        var booking = repository.findByIdForUpdate(new BookingId(command.id()))
                .orElseThrow(() -> new NoSuchElementException("Booking not found: " + command.id()));
        booking.confirm(clock.instant());
        repository.save(booking);
    }

}
