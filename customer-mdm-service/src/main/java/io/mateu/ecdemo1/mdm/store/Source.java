package io.mateu.ecdemo1.mdm.store;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Lineage: a passenger of a reservation, and the customer it was resolved to. Asking again for the
 * same passenger answers from here — which is what makes resolving idempotent — and a merge finds
 * here the reservations it has to carry the survivor's code to.
 */
@Entity
@Table(name = "customer_source", indexes = @Index(name = "source_customer", columnList = "customerId"))
@NoArgsConstructor
public class Source {

    /** {@code hotel/locator/passenger}. */
    @Id
    public String sourceKey;
    public String customerId;
    /** The customer it was first resolved to: what a merge changed, kept for audit. */
    public String firstCustomerId;
    public String hotelCode;
    public String locator;
    public int passenger;
    public Instant firstSeen;
    public Instant lastSeen;

    public static String key(String hotelCode, String locator, int passenger) {
        return hotelCode + "/" + locator + "/" + passenger;
    }
}
