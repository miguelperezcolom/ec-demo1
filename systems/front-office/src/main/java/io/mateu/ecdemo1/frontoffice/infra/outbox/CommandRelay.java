package io.mateu.ecdemo1.frontoffice.infra.outbox;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends the outbox's commands to their topics, oldest first, keyed as each command says (what is about
 * one customer or one reservation stays in order). At least once: the receivers take each command id
 * once. Only where there is a broker ({@code frontoffice.kafka-brokers}); elsewhere — a laptop, the
 * tests — the commands just stay in the outbox.
 */
@Component
@ConditionalOnExpression("'${frontoffice.kafka-brokers:}' != ''")
public class CommandRelay implements DisposableBean {

  static final Logger log = LoggerFactory.getLogger(CommandRelay.class);

  final CommandOutbox outbox;
  final Clock clock = Clock.systemUTC();
  final DefaultKafkaProducerFactory<String, byte[]> producers;
  final KafkaTemplate<String, byte[]> kafka;

  public CommandRelay(CommandOutbox outbox,
                      @org.springframework.beans.factory.annotation.Value("${frontoffice.kafka-brokers}") String brokers) {
    this.outbox = outbox;
    this.producers = new DefaultKafkaProducerFactory<>(Map.of(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers,
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class,
        ProducerConfig.MAX_BLOCK_MS_CONFIG, 10_000));
    this.kafka = new KafkaTemplate<>(producers);
  }

  @Scheduled(fixedDelayString = "${frontoffice.command-relay:1s}")
  public void relay() {
    for (var entry : outbox.pending(50)) {
      try {
        var record = new ProducerRecord<>(entry.topic(), entry.key(), entry.payload().getBytes(StandardCharsets.UTF_8));
        kafka.send(record).get(15, TimeUnit.SECONDS);
        outbox.published(entry.messageId(), clock.instant());
      } catch (Exception e) {
        // Stops here: what follows waits, so the commands about one customer never overtake each other.
        log.warn("Command {} to {} not relayed yet: {}", entry.messageId(), entry.topic(), e.toString());
        return;
      }
    }
  }

  @Override
  public void destroy() {
    producers.destroy();
  }
}
