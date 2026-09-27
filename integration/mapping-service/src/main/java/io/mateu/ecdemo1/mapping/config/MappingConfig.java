package io.mateu.ecdemo1.mapping.config;

import io.mateu.ecdemo1.mapping.outbox.OutboxProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({MappingProperties.class, OutboxProperties.class})
public class MappingConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
