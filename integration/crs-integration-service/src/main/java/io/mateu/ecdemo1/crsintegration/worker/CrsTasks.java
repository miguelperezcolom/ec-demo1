package io.mateu.ecdemo1.crsintegration.worker;

import io.mateu.ecdemo1.crsintegration.worker.runtime.LegacyTaskRefs;
import io.mateu.ecdemo1.crsintegration.worker.runtime.Reasons;
import io.mateu.workflow.worker.api.TaskRegistration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The tasks this adapter serves, each bound to its contract in ec-definitions
 * ({@code definitions/tasks/<id>.ectask}): the engine's worker runtime (worker-kafka) collects these
 * registrations and dispatches every {@code <id>@<version>} it receives on the {@value #TOPIC} topic
 * to the handler here.
 */
@Configuration
public class CrsTasks {

    public static final String TOPIC = "crs-integration";

    public static final String ANNOTATE_PMS_REFERENCE = "annotate-pms-reference";
    public static final String ANNOTATE_PARTNER_PROFILE = "annotate-partner-profile";

    @Bean
    public TaskRegistration<TaskHandlers.PmsReference, Void> annotatePmsReferenceTask(TaskHandlers handlers) {
        return new TaskRegistration<>(ANNOTATE_PMS_REFERENCE, 1, TOPIC, TaskHandlers.PmsReference.class, Void.class,
                Reasons.asBefore(handlers::annotatePmsReference));
    }

    @Bean
    public TaskRegistration<TaskHandlers.PartnerProfile, Void> annotatePartnerProfileTask(TaskHandlers handlers) {
        return new TaskRegistration<>(ANNOTATE_PARTNER_PROFILE, 1, TOPIC, TaskHandlers.PartnerProfile.class,
                Void.class, Reasons.asBefore(handlers::annotatePartnerProfile));
    }

    /**
     * The contract a step answers to when its definition does not name one yet: every step of
     * proyectar-reserva and proyectar-interlocutor today. Goes once those reference their tasks.
     */
    @Bean
    public LegacyTaskRefs legacyTaskRefs() {
        return (definitionId, stepId) -> switch (stepId) {
            case ANNOTATE_PMS_REFERENCE -> ANNOTATE_PMS_REFERENCE + "@1";
            case ANNOTATE_PARTNER_PROFILE -> ANNOTATE_PARTNER_PROFILE + "@1";
            default -> null;
        };
    }
}
