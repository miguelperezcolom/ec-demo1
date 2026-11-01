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

    /**
     * v2: the record as the forms engine writes it since EventConductor 2.31 — the lifecycle event, the
     * assignment (candidate users and groups, priority, due date), the escalation level and the business
     * key. v1 (engines 2.19–2.30) stays committed as it was: it can no longer be generated from the
     * engine's record, and {@link #everyExampleOfHumanTasksIsATaskInTheInbox} still reads its examples.
     */
    @Test
    void theSchemaOfHumanTasksIsTheFormsEnginesRecord() {
        SchemaFiles.publish(TopicSpec.topic("human-tasks").version(2)
                .describedAs("A human task created, assigned, claimed, released, completed, cancelled or escalated "
                        + "in EventConductor's forms engine (HumanTaskChanged, engine 2.31+; CloudEvent type "
                        + "io.eventconductor.task.<event>). Best effort: an inbox may lag, a task never fails for it.")
                .ownedBy("eventconductor-forms").keyedBy("taskId")
                .producedBy("eventconductor-forms").consumedBy("communication-service")
                .messages(HumanTaskChanged.class)
                .example(new HumanTaskChanged("T-1", "approve-mapping", "Aprobar equivalencias", "P-1", "approve",
                                "PENDING", List.of("mapping-approver"), null, Instant.parse("2026-11-12T09:30:00Z"),
                                HumanTaskChanged.CREATED, List.of("ana", "carlos"), List.of("mapping-approvers"), 50,
                                Instant.parse("2026-11-14T09:30:00Z"), 0, "mapping:MRU01"),
                        new HumanTaskChanged("T-1", "approve-mapping", "Aprobar equivalencias", "P-1", "approve",
                                "ASSIGNED", List.of("mapping-approver"), "carlos", Instant.parse("2026-11-12T10:00:00Z"),
                                HumanTaskChanged.CLAIMED, List.of("ana", "carlos"), List.of("mapping-approvers"), 50,
                                Instant.parse("2026-11-14T09:30:00Z"), 0, "mapping:MRU01")));
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

        // Every version still on the topic: v1 from a forms engine older than 2.31, v2 from a newer one.
        var examples = new java.util.ArrayList<>(Contracts.topic("human-tasks", 1).examples());
        examples.addAll(Contracts.topic("human-tasks", 2).examples());
        examples.forEach(json -> consumer.accept(MessageBuilder.withPayload(json.getBytes()).build()));

        var read = ArgumentCaptor.forClass(HumanTask.class);
        verify(inbox, times(examples.size())).task(read.capture());
        assertThat(read.getAllValues()).allSatisfy(t -> {
            assertThat(t.taskId()).isNotBlank();
            assertThat(t.requiredRoles()).isNotEmpty();
            assertThat(t.open()).isTrue();
        });
    }
}
