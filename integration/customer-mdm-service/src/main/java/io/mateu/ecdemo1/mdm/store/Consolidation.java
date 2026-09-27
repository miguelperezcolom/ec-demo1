package io.mateu.ecdemo1.mdm.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * The inbox for what Salesforce says: a contact carrying an MDM id left it. Keyed by the absorbed
 * customer, so the event and the poll that both report the same merge apply it once.
 */
@Entity
@Table(name = "consolidation")
@NoArgsConstructor
public class Consolidation {

    @Id
    public String absorbedId;
    /** Null when the contact was deleted rather than merged. */
    public String survivorId;
    public String absorbedContactId;
    public String survivorContactId;
    /** EVENT (Pub/Sub) or POLL (queryAll): which of the two noticed first. */
    public String via;
    public Instant receivedAt;
    public Instant appliedAt;
    /** Reservations whose customer code this merge changed, and when all of them were projected again. */
    public int reservations;
    /** {@code hotel/locator}, comma separated. */
    @Column(columnDefinition = "text")
    public String reservationKeys;
    public Instant propagatedAt;
    @Column(length = 1000)
    public String detail;

    /**
     * For a consolidation the MDM decided itself (a scanned document already belonged to the survivor):
     * whether the two contacts were merged in Salesforce too — PENDING, DONE, NOT_NEEDED (the absorbed
     * customer had no contact) or FAILED (Salesforce refused; the duplicate stays for a steward).
     * Null for a merge Salesforce made, which needs nothing.
     */
    @Column(length = 20)
    public String salesforceMerge;
    @Column(length = 1000)
    public String salesforceMergeError;
    public Instant salesforceMergedAt;
}
