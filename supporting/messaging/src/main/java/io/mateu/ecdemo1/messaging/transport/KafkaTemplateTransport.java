package io.mateu.ecdemo1.messaging.transport;

import io.mateu.ecdemo1.messaging.OutboxMessage;
import io.mateu.ecdemo1.messaging.OutboxTransport;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaOperations;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Sends with plain spring-kafka: the destination is the topic, the key the record's key, the payload
 * its UTF-8 bytes, and it waits for the broker's acknowledgement. No {@code contentType} header — a
 * Spring Cloud Stream consumer takes JSON by default — unless the message was appended with one.
 */
public class KafkaTemplateTransport implements OutboxTransport {

    final KafkaOperations<String, byte[]> kafka;
    final Duration timeout;

    public KafkaTemplateTransport(KafkaOperations<String, byte[]> kafka, Duration timeout) {
        this.kafka = kafka;
        this.timeout = timeout;
    }

    @Override
    public void send(OutboxMessage message, Map<String, byte[]> headers) throws Exception {
        var record = new ProducerRecord<>(message.destination(), message.key(),
                message.payload().getBytes(StandardCharsets.UTF_8));
        headers.forEach((name, value) -> record.headers().add(name, value));
        kafka.send(record).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
}
