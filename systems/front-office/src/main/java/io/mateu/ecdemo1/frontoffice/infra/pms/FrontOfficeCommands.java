package io.mateu.ecdemo1.frontoffice.infra.pms;

import io.mateu.ecdemo1.frontoffice.application.PmsStays;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * What the pms-fo integration tells this front office ({@code front-office-commands}): the PMS's
 * stays and its catalogue, applied by {@link PmsStays}. A command that cannot be read is logged and
 * skipped; one that fails otherwise — the database — is retried until it is applied.
 */
@Component
@ConditionalOnExpression("'${frontoffice.kafka-brokers:}' != ''")
public class FrontOfficeCommands implements DisposableBean {

  static final Logger log = LoggerFactory.getLogger(FrontOfficeCommands.class);
  public static final String GROUP = "ec-demo1-front-office-commands";
  static final JsonMapper JSON = JsonMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  final PmsStays stays;
  final ConcurrentMessageListenerContainer<String, byte[]> container;

  public FrontOfficeCommands(PmsStays stays, @Value("${frontoffice.kafka-brokers}") String brokers) {
    this.stays = stays;
    this.container = KafkaListeners.start(brokers, FrontOfficeCommand.TOPIC, GROUP, this::take);
  }

  void take(ConsumerRecord<String, byte[]> record) {
    FrontOfficeCommand command;
    try {
      command = JSON.readValue(record.value(), FrontOfficeCommand.class);
    } catch (RuntimeException e) {
      log.error("Unreadable front office command at {}-{}@{}, skipped: {}", record.topic(), record.partition(),
          record.offset(), e.getMessage());
      return;
    }
    var outcome = stays.take(command);
    log.debug("Command {} ({}): {}", command.commandId(), command.key(), outcome);
  }

  @Override
  public void destroy() {
    container.stop();
  }
}
