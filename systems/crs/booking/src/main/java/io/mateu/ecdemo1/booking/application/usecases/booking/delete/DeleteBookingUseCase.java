package io.mateu.ecdemo1.booking.application.usecases.booking.delete;

import io.mateu.ecdemo1.booking.application.usecases.booking.BookingAudit;
import io.mateu.ecdemo1.booking.application.out.repository.BookingRepository;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.Booking;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deletes bookings, all or none: see {@link Booking#requireDeletable()} for which can be. */
@Service
@RequiredArgsConstructor
public class DeleteBookingUseCase {

    final BookingRepository repository;
    final BookingAudit audit;

    @Transactional
    public void handle(DeleteBookingCommand command) {
        var ids = command.ids().stream().map(BookingId::new).toList();
        try {
            ids.forEach(id -> repository.findById(id).ifPresent(Booking::requireDeletable));
        } catch (RuntimeException e) {
            command.ids().forEach(id -> audit.failed("Booking deleted", id, null, null, e.getMessage()));
            throw e;
        }
        var hotels = new java.util.HashMap<String, String>();
        ids.forEach(id -> repository.findById(id).ifPresent(b -> hotels.put(id.id(), b.getHotelCode())));
        repository.deleteAllById(ids);
        command.ids().forEach(id -> audit.done("Booking deleted", id, hotels.get(id), null, "Borrada"));
    }

}
