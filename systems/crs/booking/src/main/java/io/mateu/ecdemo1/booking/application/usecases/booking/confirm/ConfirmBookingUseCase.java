package io.mateu.ecdemo1.booking.application.usecases.booking.confirm;

import io.mateu.ecdemo1.booking.application.usecases.booking.BookingAudit;
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
    final BookingAudit audit;

    @Transactional
    public void handle(ConfirmBookingCommand command) {
        audit.run("Booking confirmed", command.id(), hotelOf(command.id()), null, () -> {
            var booking = repository.findByIdForUpdate(new BookingId(command.id()))
                    .orElseThrow(() -> new NoSuchElementException("Booking not found: " + command.id()));
            booking.confirm(clock.instant());
            return repository.save(booking);
        }, b -> "Confirmada");
    }

    String hotelOf(String id) {
        return repository.findById(new BookingId(id)).map(b -> b.getHotelCode()).orElse(null);
    }

}
