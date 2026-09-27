package io.mateu.ecdemo1.booking.infra.out.inbox;

import io.mateu.ecdemo1.booking.application.out.inbox.Inbox;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The inbox port, on the shared inbox ({@link io.mateu.ecdemo1.messaging.Inbox}) and its table. */
@Configuration
public class SharedInbox {

    @Bean
    Inbox inbox(io.mateu.ecdemo1.messaging.Inbox inbox) {
        return inbox::firstTime;
    }
}
