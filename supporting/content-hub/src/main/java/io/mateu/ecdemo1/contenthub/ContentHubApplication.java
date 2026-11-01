package io.mateu.ecdemo1.contenthub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ContentHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentHubApplication.class, args);
    }
}
