package io.mateu.ecdemo1.booking.application.usecases.booking.changestatus;

import io.mateu.ecdemo1.booking.application.out.repository.BookingRepository;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingId;
import io.mateu.workflow.dtos.events.integration.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.NoSuchElementException;

/**
 * The payment-verification saga's worker step: confirms the booking, or cancels it for non-payment.
 * Returns the outcome to answer the engine with — the answer itself is sent by the caller, after
 * this transaction has committed. Sent from inside it, a broker that is slow to take the reply
 * keeps the booking's row locked for as long as the reply is retried, and every other change to
 * that booking waits behind it.
 *
 * <p>A booking cancelled in the meantime cannot be confirmed. That is answered as an error rather
 * than thrown: thrown, the task would be redelivered, fail the same way every time, and leave the
 * step waiting for a reply that never comes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChangeBookingStatusUseCase {

    static final String NON_PAYMENT = "IMP";

    final BookingRepository repository;
    final Clock clock;

    /** The CRS's no-show rule: the share of the original price a guest who does not arrive owes. */
    @org.springframework.beans.factory.annotation.Value("${booking.no-show-fee-percent:25}")
    int noShowFeePercent;

    /** The hotel says the guest did not arrive: cancelled as a no-show, with its fee. */
    @Transactional
    public TaskStatus noShow(String bookingId) {
        var booking = repository.findByIdForUpdate(new BookingId(bookingId))
                .orElseThrow(() -> new NoSuchElementException("Booking not found: " + bookingId));
        booking.noShow(noShowFeePercent, clock.instant());
        repository.save(booking);
        log.info("Booking {} is a no-show: it now costs {} ({}% of {})", bookingId, booking.totalAmount(), noShowFeePercent,
                booking.originalAmount());
        return TaskStatus.COMPLETED;
    }

    @Transactional
    public TaskStatus handle(ChangeBookingStatusCommand command) {
        var booking = repository.findByIdForUpdate(new BookingId(command.id()))
                .orElseThrow(() -> new NoSuchElementException("Booking not found: " + command.id()));
        try {
            switch (command.status()) {
                case Confirmed -> booking.confirm(clock.instant());
                case Cancelled -> booking.cancel(NON_PAYMENT, clock.instant());
                case Pending -> throw new IllegalArgumentException("A booking cannot be sent back to Pending");
            }
            repository.save(booking);
            return TaskStatus.COMPLETED;
        } catch (IllegalStateException e) {
            log.warn("Booking {} not changed to {}: {}", command.id(), command.status(), e.getMessage());
            return TaskStatus.ERROR;
        }
    }

}
