package io.mateu.ecdemo1.customerhistory.worker;

import io.mateu.ecdemo1.demoreset.DemoReset;
import io.mateu.ecdemo1.demoreset.DemoResetPlan;
import io.mateu.ecdemo1.demoreset.DemoResetTask;
import io.mateu.workflow.worker.api.TaskRegistration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The tasks the customer history serves, each bound to its contract in ec-definitions
 * ({@code definitions/tasks/<id>.ectask}), on the {@value #TOPIC} topic. Today only the demo's reset.
 */
@Configuration
public class CustomerHistoryTasks {

    public static final String TOPIC = "customer-history-tasks";

    /**
     * What the demo's reset (reset-demo) empties of it: the stays and the aliases. Not the inbox, as in
     * notices: the consumers' offsets stay where they are, so nothing already taken comes again.
     */
    @Bean
    public DemoResetPlan demoResetPlan() {
        return DemoResetPlan.truncate("customer-history", "customer_stay", "customer_alias");
    }

    @Bean
    public TaskRegistration<DemoResetTask.Input, Void> resetTask(DemoReset reset) {
        return DemoResetTask.registration(TOPIC, reset);
    }
}
