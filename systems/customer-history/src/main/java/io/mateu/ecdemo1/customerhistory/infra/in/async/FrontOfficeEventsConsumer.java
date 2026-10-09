package io.mateu.ecdemo1.customerhistory.infra.in.async;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.customerhistory.application.CustomerHistory;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeEvent.StayClosed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * The front office's events ({@code front-office-events}), on the consumer thread: only a closed stay
 * is history; every other variant — check-in, charges, no-shows — is someone else's, and is skipped
 * before it is read whole. A failure leaves the offset uncommitted and the event comes again; one that
 * cannot be read is logged and dropped — it would not be read the next time either.
 */
@Configuration
@Slf4j
public class FrontOfficeEventsConsumer {

    /** The discriminator of the variant it takes, as {@code FrontOfficeEvent}'s @JsonSubTypes names it. */
    public static final String STAY_CLOSED = "stay-closed";

    final CustomerHistory history;
    /** Tolerant: a field the front office adds tomorrow must not stop the history today. */
    final ObjectMapper reader;

    public FrontOfficeEventsConsumer(CustomerHistory history, ObjectMapper objectMapper) {
        this.history = history;
        this.reader = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Bean
    public Consumer<Message<byte[]>> consumeFrontOfficeEvents() {
        return message -> {
            var payload = message.getPayload();
            var type = Payloads.type(reader, payload);
            if (type == null) {
                log.error("Unreadable front office event, dropped: {}", new String(payload));
                return;
            }
            if (!STAY_CLOSED.equals(type)) {
                return;
            }
            var event = read(payload);
            if (event == null) {
                log.error("Unreadable stay-closed, dropped: {}", new String(payload));
                return;
            }
            history.take(event);
        };
    }

    /** The closed stay as the consumer reads it; null if it cannot be read. */
    public StayClosed read(byte[] payload) {
        try {
            return reader.readValue(payload, StayClosed.class);
        } catch (IOException e) {
            return null;
        }
    }
}
