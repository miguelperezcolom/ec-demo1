package io.mateu.ecdemo1.frontoffice.infra.audit;

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
 * Sends the outbox's audited actions to the {@code audit} topic, where the audit service keeps them —
 * the same topic, and the same AuditedAction, as the control plane's services. Keyed by the action's id:
 * the outbox delivers at least once and the audit service records an id only once. Only where there is
 * a broker ({@code frontoffice.kafka-brokers}); elsewhere — a laptop, the tests — the actions just stay
 * in the outbox.
 */
@Component
@ConditionalOnExpression("'${frontoffice.kafka-brokers:}' != ''")
public class AuditRelay implements DisposableBean {

  static final Logger log = LoggerFactory.getLogger(AuditRelay.class);
  static final String TOPIC = "audit";

  final AuditOutbox outbox;
  final Clock clock = Clock.systemUTC();
  final DefaultKafkaProducerFactory<String, byte[]> producers;
  final KafkaTemplate<String, byte[]> kafka;

  public AuditRelay(AuditOutbox outbox,
                    @org.springframework.beans.factory.annotation.Value("${frontoffice.kafka-brokers}") String brokers) {
    this.outbox = outbox;
    this.producers = new DefaultKafkaProducerFactory<>(Map.of(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers,
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class,
        ProducerConfig.MAX_BLOCK_MS_CONFIG, 10_000));
    this.kafka = new KafkaTemplate<>(producers);
  }

  @Scheduled(fixedDelayString = "${frontoffice.audit-relay:2s}")
  public void relay() {
    for (var entry : outbox.pending(50)) {
      try {
        var record = new ProducerRecord<>(TOPIC, entry.actionId(), entry.payload().getBytes(StandardCharsets.UTF_8));
        // No contentType header: the audit service's binder takes JSON by default, and reads the bytes.
        kafka.send(record).get(15, TimeUnit.SECONDS);
        outbox.published(entry.actionId(), clock.instant());
      } catch (Exception e) {
        log.warn("Audited action {} not relayed yet: {}", entry.actionId(), e.toString());
        return;
      }
    }
  }

  @Override
  public void destroy() {
    producers.destroy();
  }
}
