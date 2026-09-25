package io.mateu.ecdemo1.booking.application.usecases.booking.delete;

import io.mateu.ecdemo1.booking.application.out.repository.BookingRepository;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deleting is a convenience of the demo, not something a CRS does, and it announces nothing. So it
 * is refused for a booking that already reached the PMS: that one has to be cancelled, or it
 * would stay alive in the property with nothing left in the CRS to cancel it from.
 */
@Service
@RequiredArgsConstructor
public class DeleteBookingUseCase {

    final BookingRepository repository;

    @Transactional
    public void handle(DeleteBookingCommand command) {
        var ids = command.ids().stream().map(BookingId::new).toList();
        for (var id : ids) {
            repository.findById(id).ifPresent(booking -> {
                if (booking.getPmsReference() != null) {
                    throw new IllegalStateException(
                            "Booking %s is already in the PMS: cancel it instead of deleting it".formatted(id.id()));
                }
            });
        }
        repository.deleteAllById(ids);
    }

}
