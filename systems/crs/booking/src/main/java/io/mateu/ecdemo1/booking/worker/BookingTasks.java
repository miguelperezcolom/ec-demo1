package io.mateu.ecdemo1.booking.worker;

import io.mateu.ecdemo1.booking.worker.runtime.LegacyTaskRefs;
import io.mateu.ecdemo1.booking.worker.runtime.Reasons;
import io.mateu.workflow.worker.api.TaskRegistration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The tasks the CRS serves, each bound to its contract in ec-definitions
 * ({@code definitions/tasks/<id>.ectask}): the engine's worker runtime (worker-kafka) collects these
 * registrations and dispatches every {@code <id>@<version>} it receives on the {@value #TOPIC} topic
 * to the handler here.
 */
@Configuration
public class BookingTasks {

    public static final String TOPIC = "booking";

    public static final String REGISTER_NO_SHOW = "register-no-show";

    @Bean
    public TaskRegistration<TaskHandlers.NoShow, Void> registerNoShowTask(TaskHandlers handlers) {
        return new TaskRegistration<>(REGISTER_NO_SHOW, 1, TOPIC, TaskHandlers.NoShow.class, Void.class,
                Reasons.asBefore(handlers::registerNoShow));
    }

    /**
     * The contract a step answers to when the engine dispatches it with no {@code taskId}: a
     * «Registrar no-show» started on a version of the definition from before it named its task. Goes
     * once none of those is left.
     */
    @Bean
    public LegacyTaskRefs legacyTaskRefs() {
        return (definitionId, stepId) -> REGISTER_NO_SHOW.equals(stepId) ? REGISTER_NO_SHOW + "@1" : null;
    }
}
