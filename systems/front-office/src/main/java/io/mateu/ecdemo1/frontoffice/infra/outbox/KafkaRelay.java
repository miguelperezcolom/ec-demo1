package io.mateu.ecdemo1.frontoffice.infra.outbox;

import io.mateu.ecdemo1.messaging.OutboxTransport;
import io.mateu.ecdemo1.messaging.transport.KafkaTemplateTransport;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Where the shared outbox's relay sends: the topic each message names, with a producer of its own —
 * only where there is a broker ({@code frontoffice.kafka-brokers}). Elsewhere — a laptop, the tests —
 * there is no transport, and the messages stay in the outbox.
 */
@Configuration
@ConditionalOnExpression("'${frontoffice.kafka-brokers:}' != ''")
public class KafkaRelay {

  @Bean(destroyMethod = "destroy")
  DefaultKafkaProducerFactory<String, byte[]> outboxProducers(@Value("${frontoffice.kafka-brokers}") String brokers) {
    return new DefaultKafkaProducerFactory<>(Map.of(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers,
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class,
        ProducerConfig.MAX_BLOCK_MS_CONFIG, 10_000));
  }

  @Bean
  OutboxTransport outboxTransport(DefaultKafkaProducerFactory<String, byte[]> outboxProducers) {
    return new KafkaTemplateTransport(new KafkaTemplate<>(outboxProducers), Duration.ofSeconds(15));
  }
}
