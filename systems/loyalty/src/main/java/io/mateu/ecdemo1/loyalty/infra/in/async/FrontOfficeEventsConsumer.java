package io.mateu.ecdemo1.loyalty.infra.in.async;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeEvent;
import io.mateu.ecdemo1.loyalty.application.Loyalty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * The front office's events ({@code front-office-events}), on the consumer thread. Only a closed stay
 * matters here: the points it earns the members who stayed. Every other event is read and ignored. A
 * failure leaves the offset uncommitted and the event comes again (the accrual table and the inbox make
 * that harmless); one that cannot be read is logged and dropped — it would not be read the next time
 * either.
 */
@Configuration
@Slf4j
public class FrontOfficeEventsConsumer {

    final Loyalty loyalty;
    /** Tolerant: a field the front office adds tomorrow must not stop the points today. */
    final ObjectMapper reader;

    public FrontOfficeEventsConsumer(Loyalty loyalty, ObjectMapper objectMapper) {
        this.loyalty = loyalty;
        this.reader = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Bean
    public Consumer<Message<byte[]>> consumeFrontOfficeEvents() {
        return message -> {
            var event = read(message.getPayload());
            if (event == null) {
                log.error("Unreadable front office event, dropped: {}", new String(message.getPayload()));
                return;
            }
            if (event instanceof FrontOfficeEvent.StayClosed stay) {
                loyalty.accrue(stay);
            }
        };
    }

    /** The message as the consumer reads it; null if it cannot be read (a type it does not know included). */
    public FrontOfficeEvent read(byte[] payload) {
        try {
            return reader.readValue(payload, FrontOfficeEvent.class);
        } catch (IOException e) {
            return null;
        }
    }
}
