package io.mateu.ecdemo1.erp.infra.out.persistence;

import io.mateu.ecdemo1.erp.application.out.Inbox;
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
