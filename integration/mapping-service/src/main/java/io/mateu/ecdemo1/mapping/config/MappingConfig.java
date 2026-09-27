package io.mateu.ecdemo1.mapping.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(MappingProperties.class)
public class MappingConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
