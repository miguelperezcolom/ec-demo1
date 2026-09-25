package io.mateu.ecdemo1.mdm.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A change to a customer's data proposed outside Salesforce — at a hotel's reception. It is not the
 * customer's data until Salesforce, the master, approves it: the MDM opens a Case there with the
 * proposal, and its projection changes only when the contact does.
 */
@Entity
@Table(name = "change_request", indexes = @Index(name = "change_request_status", columnList = "status"))
public class ChangeRequest {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    public String id;
    public String customerId;
    /** Who proposed it, from where: e.g. "front office MRU01 · ana". */
    public String origin;
    /** The customer's data as proposed: every field, the unchanged ones as they were. */
    public String firstName;
    public String lastName;
    public String email;
    public String phone;
    public String nationality;
    public LocalDate birthDate;
    public String documentType;
    public String documentNumber;
    /** What differs from the customer's data when it was proposed, in words. */
    @Column(length = 500)
    public String changes;
    @Column(length = 20)
    public String status;
    public String salesforceCaseId;
    public Instant requestedAt;
    public Instant sentAt;
    @Column(length = 1000)
    public String sendError;
    public Instant decidedAt;
}
