package io.mateu.ecdemo1.booking.application.usecases.booking.changestatus;

import io.mateu.ecdemo1.booking.application.out.repository.BookingRepository;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingId;
import io.mateu.workflow.dtos.events.integration.TaskStatus;
import io.mateu.workflow.dtos.events.integration.TaskStatusChanged;
import io.mateu.workflow.worker.WorkerReply;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * The payment-verification saga's worker step: confirms the booking, or cancels it for non-payment,
 * and answers the engine.
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
    final StreamBridge streamBridge;
    final Clock clock;

    @Transactional
    public void handle(ChangeBookingStatusCommand command) {
        var booking = repository.findByIdForUpdate(new BookingId(command.id()))
                .orElseThrow(() -> new NoSuchElementException("Booking not found: " + command.id()));
        var outcome = TaskStatus.COMPLETED;
        try {
            switch (command.status()) {
                case Confirmed -> booking.confirm(clock.instant());
                case Cancelled -> booking.cancel(NON_PAYMENT, clock.instant());
                case Pending -> throw new IllegalArgumentException("A booking cannot be sent back to Pending");
            }
            repository.save(booking);
        } catch (IllegalStateException e) {
            log.warn("Booking {} not changed to {}: {}", command.id(), command.status(), e.getMessage());
            outcome = TaskStatus.ERROR;
        }

        WorkerReply.send(streamBridge, new TaskStatusChanged(
                command.taskExecutionId(),
                outcome,
                List.of(),
                command.processId()));
    }

}
