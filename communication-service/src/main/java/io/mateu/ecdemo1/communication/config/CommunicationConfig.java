package io.mateu.ecdemo1.communication.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.communication.send.Deliveries;
import io.mateu.ecdemo1.communication.store.Recipient;
import io.mateu.ecdemo1.communication.store.RecipientRepository;
import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Consumer;

@Configuration
@EnableScheduling
@Slf4j
public class CommunicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    /**
     * The notifications topic. Read tolerantly: a field another service adds tomorrow must not
     * stop anyone from being told anything today.
     */
    @Bean
    public Consumer<Message<byte[]>> consumeNotifications(Deliveries deliveries, ObjectMapper objectMapper) {
        var reader = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return message -> {
            try {
                deliveries.accept(reader.readValue(message.getPayload(), NotificationRequested.class));
            } catch (IOException e) {
                log.error("Unreadable notification, skipped: {}", new String(message.getPayload()), e);
            }
        };
    }

    @Bean
    ApplicationRunner seedRecipients(RecipientRepository recipients, CommunicationProperties properties) {
        return args -> {
            if (recipients.count() == 0 && properties.defaultEmail() != null && !properties.defaultEmail().isBlank()) {
                var r = new Recipient();
                r.id = UUID.randomUUID().toString();
                r.name = "Integration administrators";
                r.email = properties.defaultEmail();
                r.active = true;
                recipients.save(r);
            }
        };
    }
}
