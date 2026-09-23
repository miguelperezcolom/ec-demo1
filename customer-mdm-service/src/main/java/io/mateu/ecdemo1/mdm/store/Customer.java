package io.mateu.ecdemo1.mdm.store;

import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A golden record: who a customer is, as the MDM holds it (HLA CRM-MDM). Its id is the customer
 * code that travels to the PMS, and it never changes; a merge does not delete the absorbed record,
 * it turns it into an alias of the survivor, so every code ever handed out still resolves.
 */
@Entity
@Table(name = "customer", indexes = {
        @Index(name = "customer_email_key", columnList = "emailKey"),
        @Index(name = "customer_document_key", columnList = "documentKey"),
        @Index(name = "customer_alias_of", columnList = "aliasOf")})
@NoArgsConstructor
public class Customer {

    @Id
    public String id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public CustomerStatus status;
    /** The survivor, when this record was absorbed by a merge. */
    public String aliasOf;

    public String firstName;
    public String lastName;
    public String email;
    public String phone;
    public String nationality;
    public LocalDate birthDate;
    public String documentType;
    public String documentNumber;

    /** Normalised forms, for matching: what makes two spellings of the same thing equal. */
    public String emailKey;
    public String documentKey;

    /** Grows with every change to the record: what orders its propagation. */
    public long version;
    public Instant createdAt;
    public Instant updatedAt;

    @Enumerated(EnumType.STRING)
    public SalesforceState salesforceState;
    public String salesforceContactId;
    public Instant projectedAt;
    @Column(length = 1000)
    public String projectionError;

    /** Which value won each field in the last merge, and why: to audit it, and to undo it. */
    @Column(length = 2000)
    public String survivorship;

    public String fullName() {
        return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    }
}
