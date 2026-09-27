package io.mateu.ecdemo1.booking.infra.out.outbox;

import io.mateu.ecdemo1.booking.application.out.outbox.Outbox;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param bookingEventsBinding binding the booking events are published through
 * @param engineBinding        binding requests to the workflow engine are published through
 * @param batchSize            messages published per pass
 * @param retention            how long a published message is kept before it is purged
 */
@ConfigurationProperties("outbox")
public record OutboxProperties(String bookingEventsBinding,
                               String engineBinding,
                               int batchSize,
                               Duration retention) {

    public OutboxProperties {
        if (bookingEventsBinding == null) bookingEventsBinding = "bookingEvents";
        if (engineBinding == null) engineBinding = "outboxUpstream";
        if (batchSize <= 0) batchSize = 100;
        if (retention == null) retention = Duration.ofDays(7);
    }

    String bindingFor(Outbox.Destination destination) {
        return switch (destination) {
            case BookingEvents -> bookingEventsBinding;
            case Engine -> engineBinding;
        };
    }
}
