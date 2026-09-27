package io.mateu.ecdemo1.users;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Scheduling is on for IdentityOutboxRetired, which carries over what the old identity outbox table
// still held. The outbox relay itself is the shared outbox's (supporting/messaging), on a thread of its
// own.
@EnableScheduling
@SpringBootApplication
public class UsersApplication {

    public static void main(String[] args) {
        SpringApplication.run(UsersApplication.class, args);
    }

}
