package io.mateu.ecdemo1.partners.infra.out.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param batchSize messages published per pass
 * @param retention how long a published message is kept before it is purged
 */
@ConfigurationProperties("outbox")
public record OutboxProperties(String binding, int batchSize, Duration retention) {

    public OutboxProperties {
        if (binding == null) binding = "partnerEvents";
        if (batchSize <= 0) batchSize = 100;
        if (retention == null) retention = Duration.ofDays(7);
    }
}
