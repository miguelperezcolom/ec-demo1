package io.mateu.ecdemo1.frontoffice.infra.mdm;

import io.mateu.ecdemo1.frontoffice.infra.pms.KafkaListeners;
import io.mateu.ecdemo1.integration.model.customer.CustomerChanged;
import io.mateu.ecdemo1.integration.model.customer.CustomerEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micrometer.observation.ObservationRegistry;
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
 * What the chain's MDM says about a customer ({@code customers}), straight into the kardex: its golden
 * record and, when it decides a change the desk proposed, how it was decided. The MDM calls no one —
 * the front office is one more subscriber. A customer this front office has no guest for is nothing
 * to update here.
 */
@Component
@ConditionalOnExpression("'${frontoffice.kafka-brokers:}' != ''")
public class CustomerEvents implements DisposableBean, ConsumerPause {

  static final Logger log = LoggerFactory.getLogger(CustomerEvents.class);
  public static final String TOPIC = "customers";
  public static final String GROUP = "ec-demo1-front-office-customers";
  static final JsonMapper JSON = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  final Kardex kardex;
  final io.mateu.ecdemo1.frontoffice.domain.guest.CustomerNationalities nationalities;
  final ConcurrentMessageListenerContainer<String, byte[]> container;

  public CustomerEvents(Kardex kardex, io.mateu.ecdemo1.frontoffice.domain.guest.CustomerNationalities nationalities,
      @Value("${frontoffice.kafka-brokers}") String brokers, ObjectProvider<ObservationRegistry> observations) {
    this.kardex = kardex;
    this.nationalities = nationalities;
    this.container = KafkaListeners.start(brokers, TOPIC, GROUP, observations.getIfAvailable(), this::take);
  }

  void take(ConsumerRecord<String, byte[]> record) {
    CustomerEvent event;
    try {
      event = JSON.readValue(record.value(), CustomerEvent.class);
    } catch (RuntimeException e) {
      log.error("Unreadable customer event at {}-{}@{}, skipped: {}", record.topic(), record.partition(),
          record.offset(), e.getMessage());
      return;
    }
    apply(kardex, event);
    // The flag next to the name: the golden record's nationality, whatever it is now — kept for a
    // guest and for a companion alike (the kardex above only has the guests).
    if (event.data() != null) {
      nationalities.put(event.customerId(), event.data().nationality(), "MDM");
    }
  }

  /** The event into the kardex; false if this front office has no guest for the customer. */
  public static boolean apply(Kardex kardex, CustomerEvent event) {
    var taken = kardex.projected(event.customerId(), updateOf(event));
    if (taken) {
      log.info("{} v{} taken to the kardex{}", event.customerId(), event.version(),
          event instanceof CustomerChanged c && c.decision() != null ? " (" + c.decision() + ")" : "");
    }
    return taken;
  }

  /** The kardex's update: the customer as the MDM holds it, and the decision it answers — and why — if any. */
  public static Kardex.Update updateOf(CustomerEvent event) {
    var d = event.data();
    if (event instanceof CustomerChanged c) {
      return new Kardex.Update(d.fullName(), d.email(), d.phone(), d.documentNumber(), c.changeRequestId(),
          c.decision(), c.reason());
    }
    return new Kardex.Update(d.fullName(), d.email(), d.phone(), d.documentNumber(), null, null, null);
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
