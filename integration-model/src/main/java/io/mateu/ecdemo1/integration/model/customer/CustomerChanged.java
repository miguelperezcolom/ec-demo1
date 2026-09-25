package io.mateu.ecdemo1.integration.model.customer;

import java.time.Instant;
import java.util.List;

/**
 * A customer's golden record changed in Salesforce — by hand, or by approving a change a hotel
 * proposed — or a change a hotel proposed was decided.
 *
 * @param dataChanged     whether the data is different from before: a rejection changes nothing, and
 *                        nothing has to be written again where the customer is
 * @param changeRequestId the change request this decides, if any
 * @param decision        how it was decided (APPROVED, REJECTED), if it decides one
 */
public record CustomerChanged(String eventId, Instant occurredAt, String customerId, long version, GoldenRecord data,
                              boolean dataChanged, String changeRequestId, String decision,
                              List<String> reservations) implements CustomerEvent {
}
