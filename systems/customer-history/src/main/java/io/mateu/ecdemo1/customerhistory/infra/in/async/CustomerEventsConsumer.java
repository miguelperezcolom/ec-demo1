package io.mateu.ecdemo1.customerhistory.infra.in.async;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.customerhistory.application.CustomerHistory;
import io.mateu.ecdemo1.integration.model.customer.CustomersMerged;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * The MDM's customers ({@code customers}), on the consumer thread: only a merge matters to the history
 * — the absorbed code's stays are the survivor's from then on. A changed golden record is skipped: the
 * history keeps no customer data, only codes. Unreadable → logged and dropped, as every consumer here.
 */
@Configuration
@Slf4j
public class CustomerEventsConsumer {

    /** The discriminator of the variant it takes, as {@code CustomerEvent}'s @JsonSubTypes names it. */
    public static final String CUSTOMERS_MERGED = "customers-merged";

    final CustomerHistory history;
    /** Tolerant: a field the MDM adds tomorrow must not stop the history today. */
    final ObjectMapper reader;

    public CustomerEventsConsumer(CustomerHistory history, ObjectMapper objectMapper) {
        this.history = history;
        this.reader = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Bean
    public Consumer<Message<byte[]>> consumeCustomerEvents() {
        return message -> {
            var payload = message.getPayload();
            var type = Payloads.type(reader, payload);
            if (type == null) {
                log.error("Unreadable customer event, dropped: {}", new String(payload));
                return;
            }
            if (!CUSTOMERS_MERGED.equals(type)) {
                return;
            }
            var event = read(payload);
            if (event == null) {
                log.error("Unreadable customers-merged, dropped: {}", new String(payload));
                return;
            }
            history.take(event);
        };
    }

    /** The merge as the consumer reads it; null if it cannot be read. */
    public CustomersMerged read(byte[] payload) {
        try {
            return reader.readValue(payload, CustomersMerged.class);
        } catch (IOException e) {
            return null;
        }
    }
}
