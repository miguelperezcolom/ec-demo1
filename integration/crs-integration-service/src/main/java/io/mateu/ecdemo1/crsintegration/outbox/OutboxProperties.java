package io.mateu.ecdemo1.crsintegration.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("outbox")
public record OutboxProperties(int batchSize, Duration retention) {

    public OutboxProperties {
        if (batchSize <= 0) batchSize = 100;
        if (retention == null) retention = Duration.ofDays(7);
    }
}
