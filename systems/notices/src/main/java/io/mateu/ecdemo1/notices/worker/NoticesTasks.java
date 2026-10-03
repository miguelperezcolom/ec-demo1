package io.mateu.ecdemo1.notices.worker;

import io.mateu.ecdemo1.demoreset.DemoReset;
import io.mateu.ecdemo1.demoreset.DemoResetPlan;
import io.mateu.ecdemo1.demoreset.DemoResetTask;
import io.mateu.workflow.worker.api.TaskRegistration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The tasks the notices service serves, each bound to its contract in ec-definitions
 * ({@code definitions/tasks/<id>.ectask}), on the {@value #TOPIC} topic — not {@code notices}, which
 * is where it publishes them. Today only the demo's reset.
 */
@Configuration
public class NoticesTasks {

    public static final String TOPIC = "notices-tasks";

    /** What the demo's reset (reset-demo) empties of it — deploy/demo/zero.sh's list: the notices and the outbox. */
    @Bean
    public DemoResetPlan demoResetPlan() {
        return DemoResetPlan.truncate("notices", "notice", "outbox_message");
    }

    @Bean
    public TaskRegistration<DemoResetTask.Input, Void> resetTask(DemoReset reset) {
        return DemoResetTask.registration(TOPIC, reset);
    }
}
