package io.mateu.ecdemo1.integration.model.customer;

import java.time.Instant;
import java.util.List;

/**
 * Salesforce merged two contacts and the MDM applied its survivorship: {@code absorbedId} is now an
 * alias of {@code customerId}. The reservations are every one the survivor is on — the absorbed
 * customer's included, which now carry the survivor's code.
 */
public record CustomersMerged(String eventId, Instant occurredAt, String customerId, long version, GoldenRecord data,
                              String absorbedId, List<String> reservations) implements CustomerEvent {
}
