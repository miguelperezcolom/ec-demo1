package io.mateu.ecdemo1.integration.model.events;

import java.time.Instant;

public record ReservationModified(String eventId, Instant occurredAt, String hotelCode, String locator,
                               long version) implements IntegrationEvent {

    @Override
    public String key() {
        return hotelCode + "/" + locator;
    }
}
