package io.mateu.ecdemo1.communication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CommunicationApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommunicationApplication.class, args);
    }
}
