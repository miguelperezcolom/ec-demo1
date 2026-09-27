package io.mateu.ecdemo1.messaging.transport;

import io.mateu.ecdemo1.messaging.OutboxMessage;
import io.mateu.ecdemo1.messaging.OutboxTransport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.stream.function.StreamOperations;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Sends through Spring Cloud Stream: the destination is the binding. For a message to count as sent
 * only once the broker acknowledged it, the binding's producer must be synchronous
 * ({@code spring.cloud.stream.kafka.bindings.<binding>.producer.sync: true}), and keyed with a String
 * ({@code configuration.key.serializer: StringSerializer}) — as the services' application.yaml say.
 */
public class StreamBridgeTransport implements OutboxTransport {

    /** {@code KafkaHeaders.KEY}, without needing spring-kafka to say it. */
    static final String KAFKA_KEY = "kafka_messageKey";

    final ObjectProvider<StreamOperations> streams;

    public StreamBridgeTransport(ObjectProvider<StreamOperations> streams) {
        this.streams = streams;
    }

    @Override
    public void send(OutboxMessage message, Map<String, byte[]> headers) {
        var builder = MessageBuilder.withPayload(message.payload().getBytes(StandardCharsets.UTF_8))
                .setHeader("contentType", "application/json");
        if (message.key() != null) {
            builder.setHeader(KAFKA_KEY, message.key());
        }
        headers.forEach(builder::setHeader);
        if (!streams.getObject().send(message.destination(), builder.build())) {
            throw new IllegalStateException("The broker did not take outbox message " + message.seq());
        }
    }
}
