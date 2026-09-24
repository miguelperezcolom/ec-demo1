package io.mateu.ecdemo1.communication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
import io.mateu.ecdemo1.communication.store.DeliveryStatus;
import io.mateu.ecdemo1.communication.store.NotificationRepository;
import io.mateu.ecdemo1.communication.store.Recipient;
import io.mateu.ecdemo1.communication.store.RecipientRepository;
import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import io.mateu.ecdemo1.integration.model.notification.NotificationResolved;
import java.util.List;
import java.util.Set;
import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "communication.default-email=admins@example.com")
@Testcontainers
class CommunicationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static RedpandaContainer redpanda = new RedpandaContainer("docker.redpanda.com/redpandadata/redpanda:v24.1.7")
            .withTmpFs(Map.of("/var/lib/redpanda/data", "rw"));

    @RegisterExtension
    static GreenMailExtension smtp = new GreenMailExtension(new ServerSetup(3025, null, ServerSetup.PROTOCOL_SMTP))
            .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("KAFKA_BROKERS", redpanda::getBootstrapServers);
        registry.add("SMTP_PORT", () -> 3025);
    }

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    NotificationRepository notifications;
    @Autowired
    RecipientRepository recipients;
    @Autowired
    io.mateu.ecdemo1.communication.inbox.Inbox inbox;

    @Test
    void theSameUrgentNotificationAskedForTwiceIsEmailedOnceAndAHotelsPeopleOnlyGetTheirHotel() throws Exception {
        var palma = new Recipient();
        palma.id = UUID.randomUUID().toString();
        palma.name = "Palma front office";
        palma.email = "palma@example.com";
        palma.hotelCode = "PMI01";
        palma.active = true;
        recipients.save(palma);

        var cause = request(NotificationType.PMS_REJECTED, "PMS_REJECTED:PMI01:X", "cause-opened:PMS_REJECTED:PMI01:X:1", "PMI01");
        send(cause);
        send(cause);
        send(request(NotificationType.PMS_REJECTED, "PMS_REJECTED:CUN01:X", "cause-opened:PMS_REJECTED:CUN01:X:1", "CUN01"));

        waitFor(() -> smtp.getReceivedMessages().length >= 3);
        Thread.sleep(2000);
        // GreenMail keeps one copy per mailbox: group the copies back into the messages sent.
        var sent = new java.util.HashMap<String, java.util.Set<String>>();
        for (var m : smtp.getReceivedMessages()) {
            var recipients = sent.computeIfAbsent(m.getMessageID(), k -> new java.util.TreeSet<>());
            Arrays.stream(m.getAllRecipients()).map(Object::toString).forEach(recipients::add);
        }
        // PMI01's, once, to the administrators and Palma; CUN01's to the administrators only.
        assertThat(sent.values()).containsExactlyInAnyOrder(
                java.util.Set.of("admins@example.com", "palma@example.com"), java.util.Set.of("admins@example.com"));
        assertThat(notifications.findAll()).filteredOn(n -> n.type == NotificationType.PMS_REJECTED)
                .hasSize(2).allMatch(n -> n.status == DeliveryStatus.SENT);
    }

    @Test
    void whatIsNotUrgentGoesToTheInboxOfItsRolesOnlyAndLeavesItWhenResolved() throws Exception {
        var key = "MISSING_MAPPING:MRU01:BOARD:XX";
        var n = request(NotificationType.CAUSE_OPENED, key, "cause-opened:" + key + ":1", "MRU01");
        send(n);
        waitFor(() -> inbox.openFor(Set.of("ai-admin")).stream().anyMatch(i -> key.equals(i.subject)));

        assertThat(inbox.openFor(Set.of("ai-admin"))).filteredOn(i -> key.equals(i.subject)).singleElement()
                .satisfies(i -> {
                    assertThat(i.link).isEqualTo("https://console/mapping/causes");
                    assertThat(i.urgent).isFalse();
                });
        // Someone without the role does not see it.
        assertThat(inbox.openFor(Set.of("front-desk"))).noneMatch(i -> key.equals(i.subject));
        // Nobody is emailed.
        Thread.sleep(1500);
        assertThat(notifications.findById(n.notificationId())).get().extracting(x -> x.status).isEqualTo(DeliveryStatus.INBOX_ONLY);
        assertThat(smtp.getReceivedMessages()).noneMatch(m -> subjectOf(m).contains("Processes waiting " + key));

        send("notification-resolutions", key, new NotificationResolved(key, "ana", Instant.now()));
        waitFor(() -> inbox.openFor(Set.of("ai-admin")).stream().noneMatch(i -> key.equals(i.subject)));
        assertThat(inbox.openFor(Set.of("ai-admin"))).noneMatch(i -> key.equals(i.subject));
    }

    @Test
    void aNotificationThatArrivesAfterTheResolutionOfWhatItAnnouncedIsAlreadyResolved() throws Exception {
        var key = "integration/LATE01";
        var requested = Instant.now().minusSeconds(60);
        send("notification-resolutions", key, new NotificationResolved(key, "onboarding", Instant.now()));
        Thread.sleep(2000);
        var late = new NotificationRequested(UUID.randomUUID().toString(), NotificationType.INTEGRATION_NEEDS_ATTENTION,
                "LATE01", key, "Hotel LATE01 is ready to activate", "…", "https://console/integrations", "late:1", requested);
        send(late);
        waitFor(() -> notifications.existsByDedupKey("late:1"));
        Thread.sleep(1000);
        assertThat(inbox.openFor(Set.of("ai-admin"))).noneMatch(i -> key.equals(i.subject));
    }

    @Test
    void aTaskOfTheFormsEngineIsInTheInboxOfTheRolesItsFormRequiresUntilItIsCompleted() throws Exception {
        send("human-tasks", "t-9", Map.of("taskId", "t-9", "formId", "approve", "formName", "Approve the refund",
                "processId", "p-1", "stepId", "s1", "status", "PENDING", "requiredRoles", List.of("finance"),
                "at", Instant.now().toString(), "somethingNew", "ignored"));
        waitFor(() -> inbox.openFor(Set.of("finance")).stream().anyMatch(i -> "task/t-9".equals(i.id)));
        assertThat(inbox.openFor(Set.of("finance"))).filteredOn(i -> "task/t-9".equals(i.id)).singleElement()
                .satisfies(i -> {
                    assertThat(i.title).isEqualTo("Approve the refund");
                    assertThat(i.kind).isEqualTo("TASK");
                });
        assertThat(inbox.openFor(Set.of("ai-admin"))).noneMatch(i -> "task/t-9".equals(i.id));

        send("human-tasks", "t-9", Map.of("taskId", "t-9", "formId", "approve", "status", "COMPLETED", "userId", "luis",
                "at", Instant.now().plusSeconds(1).toString()));
        waitFor(() -> inbox.openFor(Set.of("finance")).stream().noneMatch(i -> "task/t-9".equals(i.id)));
        assertThat(inbox.openFor(Set.of("finance"))).noneMatch(i -> "task/t-9".equals(i.id));

        // A form that requires no role: everyone's.
        send("human-tasks", "t-10", Map.of("taskId", "t-10", "formId", "note", "status", "PENDING", "at", Instant.now().toString()));
        waitFor(() -> inbox.openFor(Set.of()).stream().anyMatch(i -> "task/t-10".equals(i.id)));
        assertThat(inbox.openFor(Set.of())).anyMatch(i -> "task/t-10".equals(i.id));
    }

    static String subjectOf(jakarta.mail.Message m) {
        try {
            return m.getSubject();
        } catch (jakarta.mail.MessagingException e) {
            return "";
        }
    }

    NotificationRequested request(NotificationType type, String subject, String dedupKey, String hotel) {
        return new NotificationRequested(UUID.randomUUID().toString(), type, hotel, subject,
                "Processes waiting " + subject, "Something is missing", "https://console/mapping/causes", dedupKey, Instant.now());
    }

    void send(NotificationRequested n) throws Exception {
        send("notifications", n.dedupKey(), n);
    }

    void send(String topic, String key, Object value) throws Exception {
        try (var producer = new KafkaProducer<String, String>(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                redpanda.getBootstrapServers()), new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>(topic, key, objectMapper.writeValueAsString(value))).get();
        }
    }

    static void waitFor(java.util.function.BooleanSupplier condition) throws InterruptedException {
        var deadline = System.currentTimeMillis() + 20000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(300);
        }
    }
}
