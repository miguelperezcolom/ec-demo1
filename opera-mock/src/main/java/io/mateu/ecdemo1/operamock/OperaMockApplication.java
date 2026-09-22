package io.mateu.ecdemo1.operamock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OperaMockApplication {

    public static void main(String[] args) {
        SpringApplication.run(OperaMockApplication.class, args);
    }
}
