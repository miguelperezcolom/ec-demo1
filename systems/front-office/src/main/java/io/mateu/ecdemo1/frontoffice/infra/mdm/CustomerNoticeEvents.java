package io.mateu.ecdemo1.frontoffice.infra.mdm;

import io.mateu.ecdemo1.frontoffice.application.GuestNotices;
import io.mateu.ecdemo1.frontoffice.infra.pms.KafkaListeners;
import io.mateu.ecdemo1.integration.model.customer.CustomerNoticeChanged;
import io.micrometer.observation.ObservationRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The chain's customers' reception notices, as the MDM sends them ({@code customer-notices}; their
 * master is Salesforce): kept per customer code — a guest's or a companion's — once per event (the
 * inbox), the highest version winning. Kept whether or not a stay of the customer is here yet: the
 * notice may come before the reservation does.
 */
@Component
@ConditionalOnExpression("'${frontoffice.kafka-brokers:}' != ''")
public class CustomerNoticeEvents implements DisposableBean {

  static final Logger log = LoggerFactory.getLogger(CustomerNoticeEvents.class);
  public static final String GROUP = "ec-demo1-front-office-customer-notices";
  static final JsonMapper JSON = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  final GuestNotices notices;
  final ConcurrentMessageListenerContainer<String, byte[]> container;

  public CustomerNoticeEvents(GuestNotices notices, @Value("${frontoffice.kafka-brokers}") String brokers,
      ObjectProvider<ObservationRegistry> observations) {
    this.notices = notices;
    this.container = KafkaListeners.start(brokers, CustomerNoticeChanged.TOPIC, GROUP, observations.getIfAvailable(),
        this::take);
  }

  void take(ConsumerRecord<String, byte[]> record) {
    var event = read(record.value());
    if (event == null) {
      log.error("Unreadable customer notice at {}-{}@{}, skipped", record.topic(), record.partition(), record.offset());
      return;
    }
    notices.take(event);
  }

  /** The message as the listener reads it; null if it cannot be read. */
  public static CustomerNoticeChanged read(byte[] payload) {
    try {
      return JSON.readValue(payload, CustomerNoticeChanged.class);
    } catch (RuntimeException e) {
      return null;
    }
  }

  @Override
  public void destroy() {
    container.stop();
  }
}
