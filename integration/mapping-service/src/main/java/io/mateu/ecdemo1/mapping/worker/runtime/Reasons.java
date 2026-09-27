package io.mateu.ecdemo1.mapping.worker.runtime;

import io.mateu.workflow.worker.api.ReplyNotAcceptedException;
import io.mateu.workflow.worker.api.TaskFailure;
import io.mateu.workflow.worker.api.TaskHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps a failed step's reason what it has always been here — the exception's message, as the
 * engine's step log shows it — rather than the runtime's {@code exception.toString()}. A failure is
 * still retryable: the engine retries it per the step's {@code retries}, as before.
 */
@Slf4j
public final class Reasons {

    private Reasons() {
    }

    public static <I, O> TaskHandler<I, O> asBefore(TaskHandler<I, O> handler) {
        return (input, context) -> {
            try {
                return handler.handle(input, context);
            } catch (TaskFailure | ReplyNotAcceptedException e) {
                throw e;
            } catch (RuntimeException e) {
                log.warn("Step {} of process {} failed: {}", context.stepId(), context.processId(), e.getMessage());
                throw new TaskFailure(e.getMessage() == null ? e.toString() : e.getMessage());
            }
        };
    }
}
