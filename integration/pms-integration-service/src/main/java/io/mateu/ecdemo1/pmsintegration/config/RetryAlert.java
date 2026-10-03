package io.mateu.ecdemo1.pmsintegration.config;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;

/**
 * How long a step may keep failing before someone is told (RETRY_ALERT_AFTER): the configured value,
 * which the demo may change at runtime — lower for «Opera no responde», back after — with no restart.
 */
@Slf4j
public class RetryAlert {

    private final Duration configured;
    private final AtomicReference<Duration> current;

    public RetryAlert(Duration configured) {
        this.configured = configured;
        this.current = new AtomicReference<>(configured);
    }

    public Duration after() {
        return current.get();
    }

    public Duration configured() {
        return configured;
    }

    public void set(Duration after, String by) {
        if (after == null || after.isNegative() || after.isZero()) {
            throw new IllegalArgumentException("The retry alert needs a positive duration");
        }
        log.info("Retry alert after {} (was {}), by {}", after, current.getAndSet(after), by);
    }

    public void restore(String by) {
        set(configured, by);
    }
}
