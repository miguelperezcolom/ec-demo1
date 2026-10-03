package io.mateu.ecdemo1.erp.worker;

import io.mateu.ecdemo1.demoreset.DemoReset;
import io.mateu.ecdemo1.demoreset.DemoResetPlan;
import io.mateu.ecdemo1.demoreset.DemoResetTask;
import io.mateu.workflow.worker.api.TaskRegistration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The tasks the ERP serves, each bound to its contract in ec-definitions
 * ({@code definitions/tasks/<id>.ectask}), on the {@value #TOPIC} topic. Today only the demo's
 * reset.
 */
@Configuration
public class ErpTasks {

    public static final String TOPIC = "erp";

    /**
     * What the demo's reset (reset-demo) empties of the ERP — deploy/demo/zero.sh's list: only its
     * outbox. The partners are set up, not integrated: they stay.
     */
    @Bean
    public DemoResetPlan demoResetPlan() {
        return DemoResetPlan.truncate("erp", "outbox_message");
    }

    @Bean
    public TaskRegistration<DemoResetTask.Input, Void> resetTask(DemoReset reset) {
        return DemoResetTask.registration(TOPIC, reset);
    }
}
