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
}
