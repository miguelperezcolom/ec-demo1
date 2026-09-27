package io.mateu.ecdemo1.audit.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.audit.store.AuditTrail;
import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.function.Consumer;

@Configuration
public class AuditConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** The `audit` topic, where every control-plane service publishes the actions it audits. */
    @Bean
    public Consumer<Message<byte[]>> consumeAudit(AuditTrail trail, ObjectMapper objectMapper) {
        var reader = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return message -> {
            try {
                trail.record(reader.readValue(message.getPayload(), AuditedAction.class));
            } catch (IOException e) {
                throw new UncheckedIOException("Unreadable audited action", e);
            }
        };
    }
}
