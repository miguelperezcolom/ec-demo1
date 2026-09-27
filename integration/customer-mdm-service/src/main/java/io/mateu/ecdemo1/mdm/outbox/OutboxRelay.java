package io.mateu.ecdemo1.mdm.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeTypeUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * Publishes what the outbox holds, oldest first, and marks it published.
 *
 * <p>The bindings it publishes through are synchronous (see application.yaml), so a message is
 * marked published only once the broker has acknowledged it. If the broker refuses one, the pass
 * stops there and rolls back: that message and everything after it are tried again on the next
 * pass, in the same order. A message can therefore be published twice — once acknowledged and then
 * rolled back — but never lost; consumers deduplicate by event id.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    final OutboxMessageRepository repository;
    final OutboxProperties properties;
    final StreamBridge streamBridge;
    final Clock clock;

    @Scheduled(fixedDelayString = "${outbox.interval:500ms}")
    @Transactional
    public void publishPending() {
        for (var message : repository.lockPending(properties.batchSize())) {
            var builder = MessageBuilder.withPayload(message.payload.getBytes(StandardCharsets.UTF_8))
                    .setHeader("contentType", MimeTypeUtils.APPLICATION_JSON_VALUE);
            if (message.messageKey != null) {
                builder.setHeader(KafkaHeaders.KEY, message.messageKey);
            }
            if (!streamBridge.send(message.binding, builder.build())) {
                throw new IllegalStateException("Broker did not accept outbox message " + message.seq);
            }
            message.publishedAt = clock.instant();
            log.debug("Published {} #{} to {}", message.eventType, message.seq, message.binding);
        }
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
    @Transactional
    public void purgePublished() {
        var purged = repository.deletePublishedBefore(clock.instant().minus(properties.retention()));
        if (purged > 0) {
            log.info("Purged {} published outbox messages", purged);
        }
    }
}
