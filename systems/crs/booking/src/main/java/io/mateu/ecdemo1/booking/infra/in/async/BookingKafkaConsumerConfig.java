package io.mateu.ecdemo1.booking.infra.in.async;

import io.mateu.ecdemo1.booking.application.usecases.booking.noshow.RegisterNoShowUseCase;
import io.mateu.workflow.ddd.DomainEvent;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.integration.TaskExecutionRequested;
import io.mateu.workflow.dtos.events.integration.TaskStatus;
import io.mateu.workflow.dtos.events.integration.TaskStatusChanged;
import io.mateu.workflow.worker.WorkerReply;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.function.Consumer;

/**
 * The CRS's worker: the steps of the engine's processes that name {@code topic: booking}. Today
 * that is «Registrar no-show»'s {@code register-no-show}; it answers the engine through
 * {@link WorkerReply}.
 *
 * <p>The task is handled on the consumer thread, deliberately. Handing it to a thread of its own
 * commits the offset immediately, so a reply the broker will not take has nothing left to
 * redeliver and the step waits forever.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class BookingKafkaConsumerConfig {

    final RegisterNoShowUseCase registerNoShowUseCase;
    final StreamBridge streamBridge;

    @Bean
    public Consumer<DomainEvent> consumeWorkerEvent() {
        return event -> {
            log.info("Received event: " + event);
            if (!(event instanceof TaskExecutionRequested task)) {
                return;
            }
            switch (task.stepId()) {
                // «Registrar no-show»: the hotel says the guest did not arrive. Answered only once
                // the change has committed — still on this thread, so a reply the broker will not
                // take fails the listener and the task is redelivered.
                case "register-no-show" -> {
                    registerNoShowUseCase.handle(bookingId(task));
                    WorkerReply.send(streamBridge, new TaskStatusChanged(task.taskExecutionId(), TaskStatus.COMPLETED,
                            List.of(), task.processId()));
                }
                default -> log.debug("No handler for step {}", task.stepId());
            }
        };
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
