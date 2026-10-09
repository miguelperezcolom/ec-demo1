package io.mateu.ecdemo1.loyalty.infra.config;

import io.mateu.ecdemo1.loyalty.application.Loyalty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class LoyaltyConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    /** The inbox port, on the shared inbox ({@link io.mateu.ecdemo1.messaging.Inbox}) and its table. */
    @Bean
    Loyalty.Inbox loyaltyInbox(io.mateu.ecdemo1.messaging.Inbox inbox) {
        return inbox::firstTime;
    }
}
