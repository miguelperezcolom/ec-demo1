package io.mateu.ecdemo1.booking.worker;

import io.mateu.ecdemo1.booking.application.usecases.booking.noshow.RegisterNoShowUseCase;
import io.mateu.workflow.worker.api.TaskContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The CRS's worker: the steps of the engine's processes that name {@code topic: booking}, one per task
 * contract (ec-definitions, definitions/tasks). Today that is «Registrar no-show»'s
 * {@code register-no-show}.
 */
@Component
@RequiredArgsConstructor
public class TaskHandlers {

    /** {@code register-no-show@1}'s input. A task carries every variable of its process; the rest are not its. */
    public record NoShow(String bookingId) {
    }

    final RegisterNoShowUseCase registerNoShowUseCase;

    /**
     * «Registrar no-show»: the hotel says the guest did not arrive. The runtime answers the engine
     * once this returns — after the change committed, since the use case is its own transaction.
     */
    public Void registerNoShow(NoShow input, TaskContext task) {
        if (input == null || input.bookingId() == null) {
            throw new IllegalArgumentException("Step " + task.stepId() + " needs a 'bookingId' variable");
        }
        registerNoShowUseCase.handle(input.bookingId());
        return null;
    }
}
