package io.mateu.ecdemo1.integrations.demo;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Whether the CRS takes bookings now, or a reset paused it: the Demo page shows it. A query. */
@Component
public class CrsIntake {

    public record Intake(boolean paused, Instant until, String by, String processKey) {
    }

    final RestClient booking;

    public CrsIntake(DemoProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.booking = RestClient.builder().baseUrl(properties.bookingUrl()).requestFactory(factory).build();
    }

    public Optional<Intake> status() {
        try {
            return Optional.ofNullable(booking.get().uri("/demo/intake").retrieve().body(Intake.class));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
