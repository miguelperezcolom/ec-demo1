package io.mateu.ecdemo1.audit.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.demoreset.ConsumerPause;
import io.mateu.ecdemo1.demoreset.DemoReset;
import io.mateu.ecdemo1.demoreset.StreamBindingsPause;
import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The audit's own reset, against its real schema: the trail goes, the demo's administration stays. */
@SpringBootTest(properties = {"spring.cloud.stream.function.autodetect=false", "spring.cloud.function.definition=",
        "demo-reset.settle=0s"})
@Testcontainers
class AuditResetTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    Consumer<Message<byte[]>> consumeAudit;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    DemoReset reset;
    @Autowired
    List<ConsumerPause> consumers;
    @Autowired
    JdbcTemplate jdbc;

    void deliver(String id, String action) throws Exception {
        consumeAudit.accept(MessageBuilder.withPayload(objectMapper.writeValueAsBytes(new AuditedAction(id,
                Instant.parse("2026-10-01T10:00:00Z"), "integrations", action, null, "ana", "{}", true, "ok"))).build());
    }

    @Test
    void theTrailGoesButTheDemosOwnAdministrationStays() throws Exception {
        deliver("a1", "Activate integration");
        deliver("a2", "Approve mapping");
        deliver("d1", "Demo: lanzar el reset");
        deliver("d2", "Demo: simular caída de Opera");

        var outcome = reset.run();
        reset.run(); // idempotent

        assertThat(jdbc.queryForList("select action_id from audit_record order by action_id", String.class))
                .containsExactly("d1", "d2");
        assertThat(outcome.service()).isEqualTo("audit");
        // its Spring Cloud Stream consumers are what it pauses around the reset
        assertThat(consumers).hasAtLeastOneElementOfType(StreamBindingsPause.class);
    }
}
