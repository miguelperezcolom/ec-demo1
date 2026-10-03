package io.mateu.ecdemo1.crsintegration.worker;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cloud.stream.binding.BindingService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;

/**
 * The service's part of the demo's reset (reset-demo), against a real Postgres and broker: reset@1 on
 * its own topic empties zero.sh's tables of it — every one of them in its schema — leaves the rest,
 * and its consumers run again after.
 */
@SpringBootTest
@Testcontainers
class DemoResetIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static RedpandaContainer redpanda = new RedpandaContainer("docker.redpanda.com/redpandadata/redpanda:v24.1.7")
            .withTmpFs(java.util.Map.of("/var/lib/redpanda/data", "rw,size=8g"));

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("KAFKA_BROKERS", redpanda::getBootstrapServers);
    }

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
        for (var sql : List.of("insert into outbox_message (binding, event_type, payload, created_at) values ('x', 'X', '{}', now())",
                "insert into inbox_entry (consumer, event_id, received_at) values ('c', 'e1', now())")) {
            jdbc.update(sql);
        }
        var task = (TaskRegistration<DemoResetTask.Input, Void>) registrations.stream()
                .filter(r -> r.ref().equals("reset@1")).findFirst().orElseThrow();
        assertThat(task.topic()).isEqualTo(CrsTasks.TOPIC).isEqualTo("crs-integration");

        task.handler().handle(new DemoResetTask.Input("reset-demo:1", "admin"), context());

        for (var table : List.of("inbox_entry", "outbox_message")) {
            assertThat(count(table)).as(table).isZero();
        }
        // every table the plan names is one this service has: none skipped as missing
        assertThat(reset.run().tables()).containsExactlyElementsOf(reset.plan().tables());
        assertThat(pauses).hasSize(1);
        var consumers = Arrays.stream(bindings.getConsumerBindingNames())
                .flatMap(name -> bindings.getConsumerBindings(name).stream()).toList();
        assertThat(consumers).hasSizeGreaterThan(1).allSatisfy(b -> assertThat(b.isPaused()).isFalse());
    }

    static TaskContext context() {
        return new TaskContext() {
            public String taskExecutionId() { return "te"; }
            public String processId() { return "p"; }
            public String workflowDefinitionId() { return "reset-demo"; }
            public String stepId() { return "reset-crs-integration"; }
            public boolean isCancelled() { return false; }
            public void progress(String message) { }
        };
    }
}
