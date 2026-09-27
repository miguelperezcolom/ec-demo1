package io.mateu.ecdemo1.integrations.config;

import io.mateu.ecdemo1.integrations.outbox.OutboxProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({IntegrationsProperties.class, OutboxProperties.class})
public class IntegrationsConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
