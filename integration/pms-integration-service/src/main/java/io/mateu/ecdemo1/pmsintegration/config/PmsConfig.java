package io.mateu.ecdemo1.pmsintegration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class PmsConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    /** The run's Opera context, from the environment at startup; reset-demo swaps it. */
    @Bean
    OperaContext operaContext(OhipProperties properties) {
        return OperaContext.of(properties);
    }

    /** RETRY_ALERT_AFTER, changeable at runtime (the demo page). */
    @Bean
    RetryAlert retryAlert(PmsIntegrationProperties properties) {
        return new RetryAlert(properties.alertAfter());
    }

    /** «Opera no responde», simulated (the demo page): every OHIP call fails as a timeout while on. */
    @Bean
    io.mateu.ecdemo1.pmsintegration.ohip.OperaOutage operaOutage(Clock clock,
            @org.springframework.beans.factory.annotation.Value("${ohip.outage-delay:5s}") java.time.Duration delay) {
        return new io.mateu.ecdemo1.pmsintegration.ohip.OperaOutage(clock, delay);
    }
}
