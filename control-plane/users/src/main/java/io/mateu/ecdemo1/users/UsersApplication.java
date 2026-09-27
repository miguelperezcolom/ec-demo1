package io.mateu.ecdemo1.users;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Scheduling is on for the identity outbox relay: a timer drains pending changes to Keycloak and a
// daily job purges delivered ones. See IdentityOutboxScheduler.
@EnableScheduling
@SpringBootApplication
public class UsersApplication {

    public static void main(String[] args) {
        SpringApplication.run(UsersApplication.class, args);
    }

}
