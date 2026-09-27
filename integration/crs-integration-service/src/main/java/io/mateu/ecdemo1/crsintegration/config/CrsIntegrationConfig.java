package io.mateu.ecdemo1.crsintegration.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(CrsProperties.class)
public class CrsIntegrationConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
