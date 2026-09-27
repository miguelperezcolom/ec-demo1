package io.mateu.ecdemo1.booking.application.usecases.booking.delete;

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

    @Transactional
    public void handle(DeleteBookingCommand command) {
        var ids = command.ids().stream().map(BookingId::new).toList();
        ids.forEach(id -> repository.findById(id).ifPresent(Booking::requireDeletable));
        repository.deleteAllById(ids);
    }

}
