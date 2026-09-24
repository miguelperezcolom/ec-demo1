package io.mateu.ecdemo1.mdm.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * What the hotels have to learn about a customer: its data changed in Salesforce, or a change it
 * proposed was decided. Kept until the front office and Opera have it — each one, once.
 */
@Entity
@Table(name = "hotel_update", indexes = @Index(name = "hotel_update_open", columnList = "doneAt"))
public class HotelUpdate {

    @Id
    public String id;
    public String customerId;
    /** The customer's version it carries. */
    public long version;
    /** The change request this decides, if any; and how. */
    public String requestId;
    @Column(length = 20)
    public String decision;
    /** Whether the customer's data changed — only then is Opera written. */
    public boolean dataChanged;
    public Instant createdAt;
    public Instant frontOfficeAt;
    public Instant pmsAt;
    public Instant doneAt;
    @Column(length = 1000)
    public String lastError;
}
