package io.mateu.ecdemo1.frontoffice.infra.notices;

import io.mateu.ecdemo1.frontoffice.application.GuestNotices;
import io.mateu.ecdemo1.frontoffice.infra.pms.KafkaListeners;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged;
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
 * The reception notices, as the notices service sends them ({@code notices}): a customer's (Salesforce
 * is its master), a reservation's or a partner's — kept per subject, once per event (the inbox), the
 * highest version winning. Kept whether or not a stay of the subject is here yet: the notice may come
 * before the reservation does; and kept here, not asked for, so the desk still has them when the
 * service or the network does not answer (F017).
 *
 * <p>It replaces the listener on customer-notices: the notices service takes those from the MDM and
 * publishes them here again, with the same ids and versions — a customer's notice this front office
 * already kept is kept as it was.
 */
@Component
@ConditionalOnExpression("'${frontoffice.kafka-brokers:}' != ''")
public class NoticeEvents implements DisposableBean {

  static final Logger log = LoggerFactory.getLogger(NoticeEvents.class);
  public static final String GROUP = "ec-demo1-front-office-notices";
  static final JsonMapper JSON = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  final GuestNotices notices;
  final ConcurrentMessageListenerContainer<String, byte[]> container;

  public NoticeEvents(GuestNotices notices, @Value("${frontoffice.kafka-brokers}") String brokers,
      ObjectProvider<ObservationRegistry> observations) {
    this.notices = notices;
    this.container = KafkaListeners.start(brokers, NoticeChanged.TOPIC, GROUP, observations.getIfAvailable(),
        this::take);
  }

  void take(ConsumerRecord<String, byte[]> record) {
    var event = read(record.value());
    if (event == null) {
      log.error("Unreadable notice at {}-{}@{}, skipped", record.topic(), record.partition(), record.offset());
      return;
    }
    notices.take(event);
  }

  /** The message as the listener reads it; null if it cannot be read. */
  public static NoticeChanged read(byte[] payload) {
    try {
      return JSON.readValue(payload, NoticeChanged.class);
    } catch (RuntimeException e) {
      return null;
    }
  }

  @Override
  public void destroy() {
    container.stop();
  }
}
