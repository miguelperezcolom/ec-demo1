package io.mateu.ecdemo1.mdm.worker;

import io.mateu.ecdemo1.demoreset.DemoReset;
import io.mateu.ecdemo1.demoreset.DemoResetPlan;
import io.mateu.ecdemo1.demoreset.DemoResetTask;
import io.mateu.ecdemo1.mdm.salesforce.ConsolidationEvents;
import io.mateu.ecdemo1.mdm.salesforce.SalesforceClient;
import io.mateu.workflow.worker.api.TaskFailure;
import io.mateu.workflow.worker.api.TaskRegistration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The tasks the MDM serves, each bound to its contract in ec-definitions
 * ({@code definitions/tasks/<id>.ectask}), on the {@value #TOPIC} topic: the demo's reset (process
 * reset-demo) — its own tables, and Salesforce.
 */
@Configuration
public class MdmTasks {

    public static final String TOPIC = "customer-mdm";

    public static final String CLEAN_SALESFORCE = "clean-salesforce";

    public record CleanSalesforce(String processKey) {
    }

    public record Cleaned(String salesforceDeleted) {
    }

    /**
     * The customers and all that hangs from them. The Salesforce cursors stay: clean-salesforce moves
     * them, once Salesforce is emptied too.
     */
    @Bean
    public DemoResetPlan demoResetPlan() {
        return DemoResetPlan.truncate("customer-mdm",
                "customer", "customer_document", "customer_source", "customer_xref", "consolidation", "change_request", "outbox_message");
    }

    @Bean
    public SalesforceEventsPause salesforceEventsPause(ConsolidationEvents events) {
        return new SalesforceEventsPause(events);
    }

    @Bean
    public TaskRegistration<DemoResetTask.Input, Void> resetTask(DemoReset reset) {
        return DemoResetTask.registration(TOPIC, reset);
    }

    @Bean
    public TaskRegistration<CleanSalesforce, Cleaned> cleanSalesforceTask(SalesforceCleanup cleanup) {
        return new TaskRegistration<>(CLEAN_SALESFORCE, 1, TOPIC, CleanSalesforce.class, Cleaned.class,
                (input, context) -> {
                    try {
                        return new Cleaned(cleanup.clean());
                    } catch (SalesforceClient.LimitExceeded e) {
                        throw new TaskFailure("SALESFORCE_LIMIT", e.getMessage());
                    } catch (RuntimeException e) {
                        throw new TaskFailure(e.getMessage() == null ? e.toString() : e.getMessage());
                    }
                });
    }
}
