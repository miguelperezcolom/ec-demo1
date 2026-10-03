package io.mateu.ecdemo1.crsintegration.worker;

import io.mateu.ecdemo1.crsintegration.worker.runtime.Reasons;
import io.mateu.ecdemo1.demoreset.DemoReset;
import io.mateu.ecdemo1.demoreset.DemoResetPlan;
import io.mateu.ecdemo1.demoreset.DemoResetTask;
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
    public static final String REPORT_NO_SHOW = "report-no-show";

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

    @Bean
    public TaskRegistration<TaskHandlers.NoShowToReport, TaskHandlers.NoShowReported> reportNoShowTask(TaskHandlers handlers) {
        return new TaskRegistration<>(REPORT_NO_SHOW, 1, TOPIC, TaskHandlers.NoShowToReport.class,
                TaskHandlers.NoShowReported.class, Reasons.asBefore(handlers::reportNoShow));
    }

    /** What the demo's reset (reset-demo) empties of it — deploy/demo/zero.sh's list: its inbox and outbox — it keeps no state of its own. */
    @Bean
    public DemoResetPlan demoResetPlan() {
        return DemoResetPlan.truncate("crs-integration", "inbox_entry", "outbox_message");
    }

    @Bean
    public TaskRegistration<DemoResetTask.Input, Void> resetTask(DemoReset reset) {
        return DemoResetTask.registration(TOPIC, reset);
    }
}
