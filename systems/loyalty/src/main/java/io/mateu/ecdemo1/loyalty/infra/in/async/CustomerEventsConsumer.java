package io.mateu.ecdemo1.loyalty.infra.in.async;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.customer.CustomerEvent;
import io.mateu.ecdemo1.integration.model.customer.CustomersMerged;
import io.mateu.ecdemo1.loyalty.application.Loyalty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * The MDM's customers ({@code customers}), on the consumer thread. Only a merge matters here: the
 * absorbed customer's membership moves to the survivor, the code the front office will ask by from now
 * on. A golden record that changed says nothing about loyalty, and is ignored. One that cannot be read
 * is logged and dropped.
 */
@Configuration
@Slf4j
public class CustomerEventsConsumer {

    final Loyalty loyalty;
    /** Tolerant: a field the MDM adds tomorrow must not stop the merges today. */
    final ObjectMapper reader;

    public CustomerEventsConsumer(Loyalty loyalty, ObjectMapper objectMapper) {
        this.loyalty = loyalty;
        this.reader = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Bean
    public Consumer<Message<byte[]>> consumeCustomerEvents() {
        return message -> {
            var event = read(message.getPayload());
            if (event == null) {
                log.error("Unreadable customer event, dropped: {}", new String(message.getPayload()));
                return;
            }
            if (event instanceof CustomersMerged merged) {
                loyalty.merge(merged);
            }
        };
    }

    /** The message as the consumer reads it; null if it cannot be read. */
    public CustomerEvent read(byte[] payload) {
        try {
            return reader.readValue(payload, CustomerEvent.class);
        } catch (IOException e) {
            return null;
        }
    }
}
