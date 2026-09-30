package io.mateu.ecdemo1.notices.infra.config;

import io.mateu.ecdemo1.notices.application.Notices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class NoticesConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    /** The inbox port, on the shared inbox ({@link io.mateu.ecdemo1.messaging.Inbox}) and its table. */
    @Bean
    Notices.Inbox noticesInbox(io.mateu.ecdemo1.messaging.Inbox inbox) {
        return inbox::firstTime;
    }
}
