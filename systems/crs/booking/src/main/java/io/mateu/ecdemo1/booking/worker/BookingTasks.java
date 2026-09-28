package io.mateu.ecdemo1.booking.worker;

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
}
