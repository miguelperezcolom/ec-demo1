package io.mateu.ecdemo1.contenthub.infra.config;

import io.mateu.ecdemo1.messaging.OutboxTransport;
import io.mateu.ecdemo1.messaging.transport.KafkaTemplateTransport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class ContentHubConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** The outbox relays to Kafka: content-events, and the directory snapshot requests. */
    @Bean
    OutboxTransport outboxTransport(KafkaTemplate<String, byte[]> kafka) {
        return new KafkaTemplateTransport(kafka, Duration.ofSeconds(10));
    }
}
