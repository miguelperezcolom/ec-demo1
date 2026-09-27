package io.mateu.ecdemo1.mdm.store;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Where a customer is known outside the MDM, and by what: its Salesforce contact, its guest in a
 * hotel's front office, its guest profile in Opera for a reservation. The MDM keeps them so that a
 * change of the customer — which Salesforce decides — reaches every copy of it.
 */
@Entity
@Table(name = "customer_xref", indexes = @Index(name = "customer_xref_customer", columnList = "customerId"))
public class Xref {

    public enum Target { SALESFORCE, FRONT_OFFICE, OPERA }

    /** customerId|system|reference. */
    @Id
    public String id;
    public String customerId;
    public String system;
    public String reference;
    /** Where the reference is meaningful: the hotel and reservation of an Opera profile, a front office's hotel. */
    public String context;
    public Instant seenAt;

    public static String key(String customerId, String system, String reference) {
        return customerId + "|" + system + "|" + reference;
    }
}
