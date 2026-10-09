package io.mateu.ecdemo1.customerhistory.infra.config;

import io.mateu.ecdemo1.customerhistory.application.CustomerHistory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class HistoryConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    /** The inbox port, on the shared inbox ({@link io.mateu.ecdemo1.messaging.Inbox}) and its table. */
    @Bean
    CustomerHistory.Inbox historyInbox(io.mateu.ecdemo1.messaging.Inbox inbox) {
        return inbox::firstTime;
    }
}
