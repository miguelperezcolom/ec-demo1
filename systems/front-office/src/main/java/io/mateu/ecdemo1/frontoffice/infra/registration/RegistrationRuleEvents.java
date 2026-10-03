package io.mateu.ecdemo1.frontoffice.infra.registration;

import io.mateu.ecdemo1.frontoffice.application.RegistrationRequirementsService;
import io.mateu.ecdemo1.frontoffice.infra.pms.KafkaListeners;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged;
import io.micrometer.observation.ObservationRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.mateu.ecdemo1.demoreset.ConsumerPause;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The registration rules, as the control plane sends them ({@code registration-rules}): kept, once per
 * event (the inbox), the highest version winning — every rule, whatever its scope, since which apply is
 * decided when a guest is registered. Kept here, not asked for, so the desk applies them when the
 * control plane or the network does not answer (F017).
 */
@Component
@ConditionalOnExpression("'${frontoffice.kafka-brokers:}' != ''")
public class RegistrationRuleEvents implements DisposableBean, ConsumerPause {

  static final Logger log = LoggerFactory.getLogger(RegistrationRuleEvents.class);
  public static final String GROUP = "ec-demo1-front-office-registration-rules";
  static final JsonMapper JSON = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  final RegistrationRequirementsService registration;
  final ConcurrentMessageListenerContainer<String, byte[]> container;

  public RegistrationRuleEvents(RegistrationRequirementsService registration,
      @Value("${frontoffice.kafka-brokers}") String brokers, ObjectProvider<ObservationRegistry> observations) {
    this.registration = registration;
    this.container = KafkaListeners.start(brokers, RegistrationRuleChanged.TOPIC, GROUP, observations.getIfAvailable(),
        this::take);
  }

  void take(ConsumerRecord<String, byte[]> record) {
    var rule = read(record.value());
    if (rule == null) {
      log.error("Unreadable registration rule at {}-{}@{}, skipped", record.topic(), record.partition(), record.offset());
      return;
    }
    registration.take(rule);
  }

  /** The message as the listener reads it; null if it cannot be read. */
  public static RegistrationRuleChanged read(byte[] payload) {
    try {
      return JSON.readValue(payload, RegistrationRuleChanged.class);
    } catch (RuntimeException e) {
      return null;
    }
  }

  @Override
  public void destroy() {
    container.stop();
  }

  /** Held still while the demo's reset empties the tables (demo-reset): what it had polled, finished. */
  @Override
  public void pause() {
    container.pause();
  }

  @Override
  public void resume() {
    container.resume();
  }

  @Override
  public String describe() {
    return GROUP;
  }
}
