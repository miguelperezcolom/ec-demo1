package io.mateu.ecdemo1.integration.model.customer;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.List;

/**
 * What the MDM tells whoever holds a copy of a customer (HLA CRM-MDM): its golden record changed, or
 * two customers turned out to be one. Published on the {@code customers} topic, keyed by the
 * customer, so the events of one customer stay in order. The MDM calls no one: the front office's
 * kardex and Opera's guest profiles are kept by those who subscribe, and a new system that needs
 * the customer is one more subscriber.
 *
 * <p>Each event carries the golden record as it is after it and the reservations the customer is
 * on ({@code HOTEL/LOCATOR}), so a subscriber needs to ask the MDM for nothing.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = CustomerChanged.class, name = "customer-changed"),
        @JsonSubTypes.Type(value = CustomersMerged.class, name = "customers-merged"),
})
public sealed interface CustomerEvent permits CustomerChanged, CustomersMerged {

    /** Unique per event; subscribers deduplicate on it. */
    String eventId();

    Instant occurredAt();

    /** The customer the event is about — for a merge, the survivor. The Kafka key. */
    String customerId();

    /** The customer's version after the event. */
    long version();

    GoldenRecord data();

    List<String> reservations();
}
