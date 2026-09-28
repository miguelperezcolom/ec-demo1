package io.mateu.ecdemo1.integration.model.events;

import java.time.Instant;

public record PartnerChanged(String eventId, Instant occurredAt, String partnerCode, long version)
        implements IntegrationEvent {

    @Override
    public String key() {
        return "partner/" + partnerCode;
    }
}
