package io.mateu.ecdemo1.integration.model.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;

/**
 * A business event of the integration: what changed on one side, in the integration's terms. Thin,
 * like the source events it is translated from — whoever acts on it reads the current state.
 *
 * <p>{@code key()} is the Kafka key: all events about one reservation, or one partner, stay in
 * order on one partition.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ReservationCreated.class, name = "reservation-created"),
        @JsonSubTypes.Type(value = ReservationModified.class, name = "reservation-modified"),
        @JsonSubTypes.Type(value = ReservationCancelled.class, name = "reservation-cancelled"),
        @JsonSubTypes.Type(value = PartnerChanged.class, name = "partner-changed"),
})
public sealed interface IntegrationEvent
        permits ReservationCreated, ReservationModified, ReservationCancelled, PartnerChanged {

    /** Unique per event; consumers deduplicate on it. */
    String eventId();

    Instant occurredAt();

    long version();

    String key();
}
