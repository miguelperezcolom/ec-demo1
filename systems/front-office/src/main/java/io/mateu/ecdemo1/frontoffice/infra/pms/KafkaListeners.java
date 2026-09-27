package io.mateu.ecdemo1.frontoffice.infra.pms;

import io.micrometer.observation.ObservationRegistry;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * A topic the front office takes messages from, built by hand as {@code CommandRelay} builds its
 * producer: the front office has a broker only where {@code frontoffice.kafka-brokers} says. A message
 * that fails is retried with backoff for as long as it takes — a database that is down loses nothing —
 * so whoever handles it must not throw for what retrying cannot fix: that is logged and skipped there.
 *
 * <p>Observed when there is an {@link ObservationRegistry}: a message that carries a trace
 * ({@code traceparent}) is taken inside it — a stay the PMS wrote joins the booking's trace.
 */
public final class KafkaListeners {

  private KafkaListeners() {
  }

  public static ConcurrentMessageListenerContainer<String, byte[]> start(String brokers, String topic, String group,
      Consumer<ConsumerRecord<String, byte[]>> handler) {
    return start(brokers, topic, group, null, handler);
  }

  public static ConcurrentMessageListenerContainer<String, byte[]> start(String brokers, String topic, String group,
      ObservationRegistry observations, Consumer<ConsumerRecord<String, byte[]>> handler) {
    var consumers = new DefaultKafkaConsumerFactory<String, byte[]>(Map.of(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers,
        ConsumerConfig.GROUP_ID_CONFIG, group,
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class));
    var properties = new ContainerProperties(topic);
    properties.setGroupId(group);
    properties.setMessageListener((MessageListener<String, byte[]>) handler::accept);
    if (observations != null && !observations.isNoop()) {
      properties.setObservationEnabled(true);
      properties.setObservationRegistry(observations);
    }
    var container = new ConcurrentMessageListenerContainer<>(consumers, properties);
    var backOff = new ExponentialBackOff(1_000, 2);
    backOff.setMaxInterval(60_000);
    backOff.setMaxElapsedTime(Long.MAX_VALUE);
    container.setCommonErrorHandler(new DefaultErrorHandler(backOff));
    container.setBeanName(group);
    container.start();
    return container;
  }
}
