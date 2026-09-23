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

    @Test
    void theSameNotificationAskedForTwiceIsSentOnceAndAHotelsPeopleOnlyGetTheirHotel() throws Exception {
        var palma = new Recipient();
        palma.id = UUID.randomUUID().toString();
        palma.name = "Palma front office";
        palma.email = "palma@example.com";
        palma.hotelCode = "PMI01";
        palma.active = true;
        recipients.save(palma);

        var cause = request("cause-opened:MISSING_MAPPING:PMI01:BOARD:AD:1", "PMI01");
        send(cause);
        send(cause);
        send(request("cause-opened:MISSING_MAPPING:CUN01:BOARD:AD:1", "CUN01"));

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
        assertThat(notifications.findAll()).hasSize(2).allMatch(n -> n.status == DeliveryStatus.SENT);
    }

    NotificationRequested request(String dedupKey, String hotel) {
        return new NotificationRequested(UUID.randomUUID().toString(), NotificationType.CAUSE_OPENED, hotel, "subject",
                "Processes waiting", "Something is missing", "https://console/mapping/causes", dedupKey, Instant.now());
    }

    void send(NotificationRequested n) throws Exception {
        try (var producer = new KafkaProducer<String, String>(Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                redpanda.getBootstrapServers()), new StringSerializer(), new StringSerializer())) {
            producer.send(new ProducerRecord<>("notifications", n.dedupKey(), objectMapper.writeValueAsString(n))).get();
        }
    }

    static void waitFor(java.util.function.BooleanSupplier condition) throws InterruptedException {
        var deadline = System.currentTimeMillis() + 20000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(300);
        }
    }
}
