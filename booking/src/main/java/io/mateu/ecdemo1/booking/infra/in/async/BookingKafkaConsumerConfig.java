package io.mateu.ecdemo1.booking.infra.in.async;

import io.mateu.ecdemo1.booking.application.usecases.booking.changestatus.ChangeBookingStatusCommand;
import io.mateu.ecdemo1.booking.application.usecases.booking.changestatus.ChangeBookingStatusUseCase;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.BookingStatus;
import io.mateu.workflow.ddd.DomainEvent;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.integration.TaskExecutionRequested;
import io.mateu.workflow.dtos.events.integration.TaskStatusChanged;
import io.mateu.workflow.worker.WorkerReply;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.function.StreamBridge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.function.Consumer;

/**
 * The booking worker for the saga demo: it confirms or cancels a booking on request and answers
 * the engine through {@link WorkerReply}.
 *
 * <p>The task is handled on the consumer thread, deliberately. Handing it to a thread of its own
 * — which this used to do — commits the offset immediately, so a reply the broker will not take
 * has nothing left to redeliver and the step waits forever.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class BookingKafkaConsumerConfig {

    final ChangeBookingStatusUseCase changeBookingStatusUseCase;
    final StreamBridge streamBridge;

    @Bean
    public Consumer<DomainEvent> consumeWorkerEvent() {
        return event -> {
            log.info("Received event: " + event);
            if (!(event instanceof TaskExecutionRequested task)) {
                return;
            }

            switch (task.stepId()) {
                case "confirm-booking" -> changeStatus(task, BookingStatus.Confirmed);
                case "cancel-booking" -> changeStatus(task, BookingStatus.Cancelled);
                default -> log.debug("No handler for step {}", task.stepId());
            }
        };
    }

    /**
     * Changes the booking, and only once that has committed answers the engine — still on this
     * thread, so a reply the broker will not take fails the listener and the task is redelivered.
     */
    private void changeStatus(TaskExecutionRequested task, BookingStatus status) {
        var outcome = changeBookingStatusUseCase.handle(new ChangeBookingStatusCommand(
                bookingId(task), status, task.taskExecutionId(), task.processId()));
        WorkerReply.send(streamBridge, new TaskStatusChanged(task.taskExecutionId(), outcome, List.of(), task.processId()));
    }

    private String bookingId(TaskExecutionRequested task) {
        return task.variables().stream()
                .filter(variable -> "bookingId".equals(variable.name()))
                .findAny()
                .map(Variable::value)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Step " + task.stepId() + " needs a 'bookingId' variable"));
    }

}
