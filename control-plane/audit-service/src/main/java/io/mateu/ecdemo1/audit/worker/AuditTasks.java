package io.mateu.ecdemo1.audit.worker;

import io.mateu.ecdemo1.demoreset.DemoReset;
import io.mateu.ecdemo1.demoreset.DemoResetPlan;
import io.mateu.ecdemo1.demoreset.DemoResetTask;
import io.mateu.workflow.worker.api.TaskRegistration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The tasks the audit serves, on the {@value #TOPIC} topic ({@code audit} is the trail's own topic,
 * the one the services publish their actions to): only the demo's reset (process reset-demo).
 */
@Configuration
public class AuditTasks {

    public static final String TOPIC = "audit-tasks";

    /**
     * The trail goes back to zero but for the demo's own administration — the reset launched, the
     * Opera outage simulated, whose action the Demo page audits as «Demo: …»: who did that has to
     * survive the very reset it was.
     */
    @Bean
    public DemoResetPlan demoResetPlan() {
        return new DemoResetPlan("audit", List.of(), List.of("delete from audit_record where action is null or action not like 'Demo:%'"));
    }

    @Bean
    public TaskRegistration<DemoResetTask.Input, Void> resetTask(DemoReset reset) {
        return DemoResetTask.registration(TOPIC, reset);
    }
}
