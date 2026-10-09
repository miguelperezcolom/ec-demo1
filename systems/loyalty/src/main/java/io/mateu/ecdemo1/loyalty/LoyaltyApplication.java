package io.mateu.ecdemo1.loyalty;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Riu Class, the chain's loyalty programme — a demo one: there is no real loyalty system in this PoC.
 * The front office asks it a guest's tier and points over HTTP, and a closed stay really earns points.
 */
@SpringBootApplication
public class LoyaltyApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoyaltyApplication.class, args);
    }
}
