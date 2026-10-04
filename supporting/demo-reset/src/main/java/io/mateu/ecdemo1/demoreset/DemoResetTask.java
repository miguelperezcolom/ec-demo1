package io.mateu.ecdemo1.demoreset;

import io.mateu.workflow.worker.api.TaskFailure;
import io.mateu.workflow.worker.api.TaskRegistration;

/**
 * The engine's task {@code reset@1} (ec-definitions, definitions/tasks/reset.ectask) on a service's
 * own topic: each step of reset-demo that resets a service names that service's topic. Declared by
 * each service among its task registrations, so its ServedTasksTest lists it:
 *
 * <pre>{@code
 * @Bean TaskRegistration<DemoResetTask.Input, Void> resetTask(DemoReset reset) {
 *     return DemoResetTask.registration(TOPIC, reset);
 * }
 * }</pre>
 */
public final class DemoResetTask {

    public static final String ID = "reset";
    public static final int VERSION = 1;

    /** Who asked, for the logs; the reset needs nothing else. */
    public record Input(String processKey, String launchedBy) {
    }

    private DemoResetTask() {
    }

    public static TaskRegistration<Input, Void> registration(String topic, DemoReset reset) {
        return new TaskRegistration<>(ID, VERSION, topic, Input.class, Void.class, (input, context) -> {
            try {
                var outcome = reset.run();
                context.progress(outcome.summary());
                return null;
            } catch (RuntimeException e) {
                throw new TaskFailure("RESET_FAILED",
                        (reset.plan() == null ? "" : reset.plan().service() + ": ")
                                + (e.getMessage() == null ? e.toString() : e.getMessage()));
            }
        });
    }
}
