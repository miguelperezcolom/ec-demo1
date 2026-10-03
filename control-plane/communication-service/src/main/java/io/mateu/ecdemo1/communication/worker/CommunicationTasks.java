package io.mateu.ecdemo1.communication.worker;

import io.mateu.ecdemo1.demoreset.DemoReset;
import io.mateu.ecdemo1.demoreset.DemoResetPlan;
import io.mateu.ecdemo1.demoreset.DemoResetTask;
import io.mateu.workflow.worker.api.TaskRegistration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The tasks this service serves, on the {@value #TOPIC} topic: only the demo's reset (process
 * reset-demo).
 */
@Configuration
public class CommunicationTasks {

    public static final String TOPIC = "communication";

    /**
     * The inboxes and what was sent go; who hears of what (the recipients), the announcements and the
     * browsers that allowed notifications stay — they are set up, not made by the integration.
     */
    @Bean
    public DemoResetPlan demoResetPlan() {
        return DemoResetPlan.truncate("communication", "inbox_item", "inbox_seen", "notification", "resolution");
    }

    @Bean
    public TaskRegistration<DemoResetTask.Input, Void> resetTask(DemoReset reset) {
        return DemoResetTask.registration(TOPIC, reset);
    }
}
