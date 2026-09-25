package io.mateu.ecdemo1.pmsintegration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PmsIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(PmsIntegrationApplication.class, args);
    }
}
