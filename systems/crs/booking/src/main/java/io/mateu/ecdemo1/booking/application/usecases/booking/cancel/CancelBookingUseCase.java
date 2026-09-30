package io.mateu.ecdemo1.booking.application.usecases.booking.cancel;

import io.mateu.ecdemo1.booking.application.usecases.booking.BookingAudit;
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
    final BookingAudit audit;

    @Transactional
    public void handle(CancelBookingCommand command) {
        var hotel = repository.findById(new BookingId(command.id())).map(b -> b.getHotelCode()).orElse(null);
        audit.run("Booking cancelled", command.id(), hotel, BookingAudit.params("reason", command.reasonCode()), () -> {
            var booking = repository.findByIdForUpdate(new BookingId(command.id()))
                    .orElseThrow(() -> new NoSuchElementException("Booking not found: " + command.id()));
            // A hotel may cancel with reasons of its own: checked against the booking's hotel.
            var reason = catalog.cancellationReason(booking.getHotelCode(), command.reasonCode());
            booking.cancel(reason.code(), clock.instant());
            return repository.save(booking);
        }, b -> "Cancelada · motivo " + command.reasonCode());
    }

}
