package io.mateu.ecdemo1.notices.infra.in.async;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.customer.CustomerNoticeChanged;
import io.mateu.ecdemo1.notices.application.Notices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * The customers' notices as the MDM sends them ({@code customer-notices}; Salesforce is their master),
 * on the consumer thread: kept and published again on {@code notices}, once each (the inbox). A
 * failure leaves the offset uncommitted and the event comes again; one that cannot be read is logged
 * and dropped — it would not be read the next time either.
 */
@Configuration
@Slf4j
public class CustomerNoticesConsumer {

    final Notices notices;
    /** Tolerant: a field the MDM adds tomorrow must not stop the notices today. */
    final ObjectMapper reader;

    public CustomerNoticesConsumer(Notices notices, ObjectMapper objectMapper) {
        this.notices = notices;
        this.reader = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Bean
    public Consumer<Message<byte[]>> consumeCustomerNotices() {
        return message -> {
            var event = read(message.getPayload());
            if (event == null) {
                log.error("Unreadable customer notice, dropped: {}", new String(message.getPayload()));
                return;
            }
            notices.take(event);
        };
    }

    /** The message as the consumer reads it; null if it cannot be read. */
    public CustomerNoticeChanged read(byte[] payload) {
        try {
            return reader.readValue(payload, CustomerNoticeChanged.class);
        } catch (IOException e) {
            return null;
        }
    }
}
