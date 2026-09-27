package io.mateu.ecdemo1.integrations.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(IntegrationsProperties.class)
public class IntegrationsConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
