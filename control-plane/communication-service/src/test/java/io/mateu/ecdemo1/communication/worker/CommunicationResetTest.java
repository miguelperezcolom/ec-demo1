package io.mateu.ecdemo1.communication.worker;

import static org.assertj.core.api.Assertions.assertThat;

import io.mateu.ecdemo1.communication.inbox.HumanTask;
import io.mateu.ecdemo1.communication.inbox.Inbox;
import io.mateu.ecdemo1.communication.store.Notification;
import io.mateu.ecdemo1.communication.store.NotificationRepository;
import io.mateu.ecdemo1.demoreset.ConsumerPause;
import io.mateu.ecdemo1.demoreset.DemoReset;
import io.mateu.ecdemo1.demoreset.StreamBindingsPause;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** This service's own reset, against its real schema: the inboxes go, who hears of what stays. */
@SpringBootTest(properties = {"spring.cloud.stream.function.autodetect=false", "spring.cloud.function.definition=",
        "demo-reset.settle=0s"})
@Testcontainers
class CommunicationResetTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    Inbox inbox;
    @Autowired
    NotificationRepository notifications;
    @Autowired
    DemoReset reset;
    @Autowired
    List<ConsumerPause> consumers;
    @Autowired
    JdbcTemplate jdbc;

    int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void theInboxesAndNotificationsGoTheRecipientsStay() {
        inbox.task(new HumanTask("t1", "confirmar-reset-demo", "Confirmar", "p1", "confirm", "PENDING",
                List.of("ai-admin"), null, Instant.now()));
        inbox.markSeen(List.of("task/t1"), "ana");
        inbox.resolve("retrying:p2/s", "ana", Instant.now());
        var n = new Notification();
        n.id = "n1";
        n.dedupKey = "n1";
        n.requestedAt = Instant.now();
        notifications.save(n);
        var recipients = count("recipient");
        assertThat(recipients).isPositive(); // seeded with the defaults
        assertThat(count("inbox_item")).isPositive();
        assertThat(count("inbox_seen")).isPositive();
        assertThat(count("resolution")).isPositive();

        reset.run();
        reset.run(); // idempotent

        assertThat(count("inbox_item")).isZero();
        assertThat(count("inbox_seen")).isZero();
        assertThat(count("resolution")).isZero();
        assertThat(count("notification")).isZero();
        assertThat(count("recipient")).isEqualTo(recipients);
        assertThat(consumers).hasAtLeastOneElementOfType(StreamBindingsPause.class);
    }
}
