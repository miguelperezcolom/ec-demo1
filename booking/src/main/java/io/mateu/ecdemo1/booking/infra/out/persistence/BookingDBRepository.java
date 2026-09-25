package io.mateu.ecdemo1.booking.infra.out.persistence;

import io.mateu.ecdemo1.booking.application.out.outbox.Outbox;
import io.mateu.ecdemo1.booking.application.out.repository.BookingRepository;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.Booking;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.events.BookingEvent;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingId;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookingDBRepository implements BookingRepository {

    final BookingEntityRepository repository;
    final EntityManager entityManager;
    final Outbox outbox;

    @Override
    public Optional<Booking> findById(BookingId id) {
        return repository.findById(id.id()).map(BookingMapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Booking> findByIdForUpdate(BookingId id) {
        return repository.findByIdForUpdate(id.id()).map(BookingMapper::toDomain);
    }

    /**
     * Writes the booking and, in the same transaction, the events it recorded to the outbox.
     *
     * <p>The events must continue the stored version with no gap and no overlap. They always do
     * when the change was made on a booking read with {@link #findByIdForUpdate}; the check is
     * there so that a change made on a copy read without the lock fails here, loudly, instead of
     * publishing a version number that another change has already used.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BookingId save(Booking booking) {
        var events = booking.popEvents().stream().map(BookingEvent.class::cast).toList();
        var existing = repository.findById(booking.getId().id());
        if (existing.isPresent()) {
            var stored = existing.get().getVersion();
            if (!events.isEmpty() && events.getFirst().version() != stored + 1) {
                throw new IllegalStateException("Booking %s changed concurrently: stored version %d, change starts at %d"
                        .formatted(booking.getId().id(), stored, events.getFirst().version()));
            }
            BookingMapper.copy(booking, existing.get());
        } else {
            var entity = new BookingEntity();
            BookingMapper.copy(booking, entity);
            entityManager.persist(entity);
        }
        events.forEach(event -> outbox.append(Outbox.Destination.BookingEvents, event));
        return booking.getId();
    }

    @Override
    public void deleteAllById(List<BookingId> selectedIds) {
        repository.deleteAllById(selectedIds.stream().map(BookingId::id).toList());
    }

}
