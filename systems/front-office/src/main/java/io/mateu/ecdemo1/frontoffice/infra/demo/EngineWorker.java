package io.mateu.ecdemo1.frontoffice.infra.demo;

import io.mateu.ecdemo1.demoreset.DemoReset;
import io.mateu.ecdemo1.frontoffice.infra.pms.KafkaListeners;
import io.micrometer.observation.ObservationRegistry;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * The front office's engine worker: the tasks on the {@value EngineTasks#TOPIC} topic, run by
 * {@link EngineTasks} and answered on {@value EngineTasks#REPLIES}. The answer is sent before the task's
 * offset is committed: one the broker did not take is the task again, later — the reset is idempotent.
 *
 * <p>Not a {@link io.mateu.ecdemo1.demoreset.ConsumerPause}: it is what runs the reset, and its answer
 * has to leave.
 */
@Component
@ConditionalOnExpression("'${frontoffice.kafka-brokers:}' != ''")
public class EngineWorker implements DisposableBean {

  public static final String GROUP = "ec-demo1-front-office-worker";

  final EngineTasks tasks;
  final KafkaTemplate<String, byte[]> replies;
  final ConcurrentMessageListenerContainer<String, byte[]> container;

  public EngineWorker(DemoReset reset, DefaultKafkaProducerFactory<String, byte[]> outboxProducers,
      @Value("${frontoffice.kafka-brokers}") String brokers, ObjectProvider<ObservationRegistry> observations) {
    this.tasks = new EngineTasks(reset);
    this.replies = new KafkaTemplate<>(outboxProducers);
    this.container = KafkaListeners.start(brokers, EngineTasks.TOPIC, GROUP, observations.getIfAvailable(), this::take);
  }

  void take(ConsumerRecord<String, byte[]> record) {
    for (var reply : tasks.handle(record.value())) {
      var message = new ProducerRecord<String, byte[]>(EngineTasks.REPLIES, reply.key(), reply.payload());
      message.headers().add("contentType", "application/json".getBytes());
      try {
        replies.send(message).get(15, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted answering the engine", e);
      } catch (Exception e) {
        // Retried by the listener (KafkaListeners' backoff): the task runs again, idempotent.
        throw new IllegalStateException("The broker did not take the answer to the engine: " + e.getMessage(), e);
      }
    }
  }

  @Override
  public void destroy() {
    container.stop();
  }
}
