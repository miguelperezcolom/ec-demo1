package io.mateu.ecdemo1.communication.contracts;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.communication.config.CommunicationConfig;
import io.mateu.ecdemo1.communication.inbox.HumanTask;
import io.mateu.ecdemo1.communication.inbox.Inbox;
import io.mateu.ecdemo1.communication.send.Deliveries;
import io.mateu.ecdemo1.contracts.testing.Contracts;
import io.mateu.ecdemo1.contracts.testing.SchemaFiles;
import io.mateu.ecdemo1.contracts.testing.TopicSpec;
import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import io.mateu.ecdemo1.integration.model.notification.NotificationResolved;
import io.mateu.workflow.dtos.events.integration.HumanTaskChanged;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * What communication-service reads: every example of notifications, notification-resolutions and
 * human-tasks goes through its consumers as the topic would deliver it, and none is skipped as
 * unreadable. human-tasks is the forms engine's topic; its schema is generated here, from the
 * engine's own record, as the one service that reads it.
 */
class CommunicationContractsTest {

    static ObjectMapper bootMapper() {
        try (var context = new AnnotationConfigApplicationContext(JacksonAutoConfiguration.class)) {
            return context.getBean(ObjectMapper.class);
        }
    }

    final ObjectMapper boot = bootMapper();
    final CommunicationConfig config = new CommunicationConfig();

    @Test
    void theSchemaOfHumanTasksIsTheFormsEnginesRecord() {
        // v2: since engine 2.31 the record also carries the lifecycle event and the assignment (event,
        // candidates, priority, due date, escalations, business key) — v1, closed, cannot hold them.
        SchemaFiles.publish(TopicSpec.topic("human-tasks").version(2)
                .describedAs("A human task created, assigned, claimed, released, completed, cancelled or escalated in "
                        + "EventConductor's forms engine (HumanTaskChanged, engine 2.31+: with the lifecycle event and the "
                        + "assignment). Best effort: an inbox may lag, a task never fails for it.")
                .ownedBy("eventconductor-forms").keyedBy("taskId")
                .producedBy("eventconductor-forms").consumedBy("communication-service")
                .messages(HumanTaskChanged.class)
                .example(new HumanTaskChanged("T-1", "approve-mapping", "Aprobar equivalencias", "P-1", "approve",
                        "PENDING", List.of("mapping-approver"), null, Instant.parse("2026-11-12T09:30:00Z"),
                        HumanTaskChanged.CREATED, List.of(), List.of("mapping-approver"), 50, null, 0,
                        "alta-integracion:52e45d18-00d1-48b9-b792-4f2e723e6efb"))
                .example(new HumanTaskChanged("T-1", "approve-mapping", "Aprobar equivalencias", "P-1", "approve",
                        "COMPLETED", List.of("mapping-approver"), "demo", Instant.parse("2026-11-12T09:41:00Z"),
                        HumanTaskChanged.COMPLETED, List.of(), List.of("mapping-approver"), 50,
                        Instant.parse("2026-11-13T09:30:00Z"), 0, "alta-integracion:52e45d18-00d1-48b9-b792-4f2e723e6efb")));
    }

    @Test
    void everyExampleOfNotificationsIsDelivered() {
        var deliveries = mock(Deliveries.class);
        var consumer = config.consumeNotifications(deliveries, boot);

        var examples = Contracts.topic("notifications").examples();
        examples.forEach(json -> consumer.accept(MessageBuilder.withPayload(json.getBytes()).build()));

        var read = ArgumentCaptor.forClass(NotificationRequested.class);
        verify(deliveries, times(examples.size())).accept(read.capture());
        assertThat(read.getAllValues()).allSatisfy(n -> {
            assertThat(n.notificationId()).isNotBlank();
            assertThat(n.type()).isNotNull();
            assertThat(n.requestedAt()).isNotNull();
        });
    }

    @Test
    void everyExampleOfNotificationResolutionsResolvesItsSubject() {
        var inbox = mock(Inbox.class);
        var consumer = config.consumeResolutions(inbox, boot);

        var examples = Contracts.topic("notification-resolutions").examples();
        examples.forEach(json -> consumer.accept(MessageBuilder.withPayload(json.getBytes()).build()));

        var read = ArgumentCaptor.forClass(NotificationResolved.class);
        verify(inbox, times(examples.size())).resolved(read.capture());
        assertThat(read.getAllValues()).allSatisfy(r -> assertThat(r.subject()).isNotBlank());
    }

    @Test
    void everyExampleOfHumanTasksIsATaskInTheInbox() {
        var inbox = mock(Inbox.class);
        var consumer = config.consumeHumanTasks(inbox, boot);

        var examples = Contracts.topic("human-tasks").examples();
        examples.forEach(json -> consumer.accept(MessageBuilder.withPayload(json.getBytes()).build()));

        var read = ArgumentCaptor.forClass(HumanTask.class);
        verify(inbox, times(examples.size())).task(read.capture());
        assertThat(read.getAllValues()).allSatisfy(t -> {
            assertThat(t.taskId()).isNotBlank();
            assertThat(t.requiredRoles()).isNotEmpty();
            // a pending task opens in the inbox, a completed one closes it there
            assertThat(t.open()).isEqualTo("PENDING".equals(t.status()) || "ASSIGNED".equals(t.status()));
        });
        assertThat(read.getAllValues()).extracting(HumanTask::open).contains(true, false);
    }
}
