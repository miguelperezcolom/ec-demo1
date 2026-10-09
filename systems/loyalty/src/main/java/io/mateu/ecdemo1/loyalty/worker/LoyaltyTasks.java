package io.mateu.ecdemo1.loyalty.worker;

import io.mateu.ecdemo1.demoreset.DemoReset;
import io.mateu.ecdemo1.demoreset.DemoResetPlan;
import io.mateu.ecdemo1.demoreset.DemoResetTask;
import io.mateu.workflow.worker.api.TaskRegistration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The tasks the loyalty service serves, each bound to its contract in ec-definitions
 * ({@code definitions/tasks/<id>.ectask}), on the {@value #TOPIC} topic. Today only the demo's reset.
 */
@Configuration
public class LoyaltyTasks {

    public static final String TOPIC = "loyalty-tasks";

    /**
     * What the demo's reset (reset-demo) empties of it: the members and what they earned. The members
     * come back from the demo's seeding (PUT /members/…), not from here. The inbox is left, as notices
     * leaves its own: the event ids it holds will not come again.
     */
    @Bean
    public DemoResetPlan demoResetPlan() {
        return DemoResetPlan.truncate("loyalty", "member", "accrual");
    }

    @Bean
    public TaskRegistration<DemoResetTask.Input, Void> resetTask(DemoReset reset) {
        return DemoResetTask.registration(TOPIC, reset);
    }
}
