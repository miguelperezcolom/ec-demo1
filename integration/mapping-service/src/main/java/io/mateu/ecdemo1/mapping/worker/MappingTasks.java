package io.mateu.ecdemo1.mapping.worker;

import io.mateu.ecdemo1.mapping.worker.runtime.Reasons;
import io.mateu.workflow.worker.api.TaskRegistration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The tasks the mapping serves, each bound to its contract in ec-definitions
 * ({@code definitions/tasks/<id>.ectask}): the engine's worker runtime (worker-kafka) collects these
 * registrations and dispatches every {@code <id>@<version>} it receives on the {@value #TOPIC} topic
 * to the handler here.
 */
@Configuration
public class MappingTasks {

    public static final String TOPIC = "mapping";

    public static final String PREPARE_RESERVATION = "prepare-reservation";
    public static final String PREPARE_CANCELLATION = "prepare-cancellation";
    public static final String PREPARE_PARTNER = "prepare-partner";
    public static final String RELAUNCH_PROCESS = "relaunch-process";
    public static final String RECORD_PARTNER_PROFILE = "record-partner-profile";
    public static final String RESOLVE_PROJECTION = "resolve-projection";

    @Bean
    public TaskRegistration<TaskHandlers.Subject, TaskHandlers.PrepareOutcome> prepareReservationTask(TaskHandlers handlers) {
        return new TaskRegistration<>(PREPARE_RESERVATION, 1, TOPIC, TaskHandlers.Subject.class,
                TaskHandlers.PrepareOutcome.class, Reasons.asBefore(handlers::prepareReservation));
    }

    @Bean
    public TaskRegistration<TaskHandlers.Subject, TaskHandlers.PrepareOutcome> prepareCancellationTask(TaskHandlers handlers) {
        return new TaskRegistration<>(PREPARE_CANCELLATION, 1, TOPIC, TaskHandlers.Subject.class,
                TaskHandlers.PrepareOutcome.class, Reasons.asBefore(handlers::prepareCancellation));
    }

    @Bean
    public TaskRegistration<TaskHandlers.Subject, TaskHandlers.PrepareOutcome> preparePartnerTask(TaskHandlers handlers) {
        return new TaskRegistration<>(PREPARE_PARTNER, 1, TOPIC, TaskHandlers.Subject.class,
                TaskHandlers.PrepareOutcome.class, Reasons.asBefore(handlers::preparePartner));
    }

    @Bean
    public TaskRegistration<TaskHandlers.Released, TaskHandlers.Successor> relaunchProcessTask(TaskHandlers handlers) {
        return new TaskRegistration<>(RELAUNCH_PROCESS, 1, TOPIC, TaskHandlers.Released.class,
                TaskHandlers.Successor.class, Reasons.asBefore(handlers::relaunch));
    }

    @Bean
    public TaskRegistration<TaskHandlers.PartnerProfileRecorded, Void> recordPartnerProfileTask(TaskHandlers handlers) {
        return new TaskRegistration<>(RECORD_PARTNER_PROFILE, 1, TOPIC, TaskHandlers.PartnerProfileRecorded.class,
                Void.class, Reasons.asBefore(handlers::recordPartnerProfile));
    }

    @Bean
    public TaskRegistration<TaskHandlers.Projected, Void> resolveProjectionTask(TaskHandlers handlers) {
        return new TaskRegistration<>(RESOLVE_PROJECTION, 1, TOPIC, TaskHandlers.Projected.class, Void.class,
                Reasons.asBefore(handlers::resolveProjection));
    }
}
