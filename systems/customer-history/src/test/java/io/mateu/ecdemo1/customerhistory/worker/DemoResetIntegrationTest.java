package io.mateu.ecdemo1.customerhistory.worker;

import static org.assertj.core.api.Assertions.assertThat;

import io.mateu.ecdemo1.customerhistory.Containers;
import io.mateu.ecdemo1.demoreset.DemoReset;
import io.mateu.ecdemo1.demoreset.DemoResetTask;
import io.mateu.ecdemo1.demoreset.StreamBindingsPause;
import io.mateu.workflow.worker.api.TaskContext;
import io.mateu.workflow.worker.api.TaskRegistration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binding.BindingService;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The service's part of the demo's reset (reset-demo), against a real Postgres and broker: reset@1 on
 * its own topic empties its tables — every one of them in its schema — and its consumers run again after.
 */
@SpringBootTest
class DemoResetIntegrationTest extends Containers {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    DemoReset reset;
    @Autowired
    List<TaskRegistration<?, ?>> registrations;
    @Autowired
    BindingService bindings;
    @Autowired
    List<StreamBindingsPause> pauses;

    int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void resetEmptiesItsOwnTablesOnItsOwnTopicAndItsConsumersRunAgain() throws Exception {
        for (var sql : List.of("insert into customer_alias (absorbed_id, survivor_id) values ('C-2', 'C-1')",
                "insert into customer_stay (id, customer_id, stay_id, nights, holder, source) values ('S|C-1', 'C-1', 'S', 3, true, 'DEMO')")) {
            jdbc.update(sql);
        }
        var task = (TaskRegistration<DemoResetTask.Input, Void>) registrations.stream()
                .filter(r -> r.ref().equals("reset@1")).findFirst().orElseThrow();
        assertThat(task.topic()).isEqualTo(CustomerHistoryTasks.TOPIC).isEqualTo("customer-history-tasks");

        task.handler().handle(new DemoResetTask.Input("reset-demo:1", "admin"), context());

        for (var table : List.of("customer_stay", "customer_alias")) {
            assertThat(count(table)).as(table).isZero();
        }
        // every table the plan names is one this service has: none skipped as missing
        assertThat(reset.run().tables()).containsExactlyElementsOf(reset.plan().tables());
        assertThat(pauses).hasSize(1);
        var consumers = Arrays.stream(bindings.getConsumerBindingNames())
                .flatMap(name -> bindings.getConsumerBindings(name).stream()).toList();
        assertThat(consumers).hasSizeGreaterThan(2).allSatisfy(b -> assertThat(b.isPaused()).isFalse());
    }

    static TaskContext context() {
        return new TaskContext() {
            public String taskExecutionId() { return "te"; }
            public String processId() { return "p"; }
            public String workflowDefinitionId() { return "reset-demo"; }
            public String stepId() { return "reset-customer-history"; }
            public boolean isCancelled() { return false; }
            public void progress(String message) { }
        };
    }
}
