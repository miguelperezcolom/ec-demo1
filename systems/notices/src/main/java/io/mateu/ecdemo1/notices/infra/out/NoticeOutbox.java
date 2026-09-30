package io.mateu.ecdemo1.notices.infra.out;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged;
import io.mateu.ecdemo1.messaging.Outbox;
import io.mateu.ecdemo1.notices.application.Notices;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every change of a notice to the shared outbox, in the change's own transaction; its relay publishes
 * it through the {@code noticeEvents} binding (the {@code notices} topic), keyed by the subject.
 */
@Component
public class NoticeOutbox implements Notices.Events {

    public static final String BINDING = "noticeEvents";

    /** Where the outbox is written: the shared one, or — in a test — anything that takes the message. */
    public interface Sink {
        void append(String binding, String key, String type, String payload);
    }

    final Sink sink;
    final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public NoticeOutbox(Outbox outbox, ObjectMapper objectMapper) {
        this((binding, key, type, payload) -> outbox.append(binding, key, type, payload, null), objectMapper);
    }

    public NoticeOutbox(Sink sink, ObjectMapper objectMapper) {
        this.sink = sink;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(NoticeChanged event) {
        write(event);
    }

    /** The message as it goes: the record, with the application's mapper. */
    public void write(NoticeChanged event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + event, e);
        }
        sink.append(BINDING, event.key(), "NoticeChanged", payload);
    }
}
