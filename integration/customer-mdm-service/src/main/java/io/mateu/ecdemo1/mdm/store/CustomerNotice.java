package io.mateu.ecdemo1.mdm.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A reception notice of a customer — what the desk must know when they arrive, stay or leave. Its
 * master is Salesforce, where it is a Case on the contact with a «Tipo de aviso»; the MDM keeps it as
 * Salesforce last said it (what the hotels are told, on customer-notices) and, apart, what the
 * Clientes console asked for and Salesforce has not confirmed yet.
 */
@Entity
@Table(name = "customer_notice", indexes = {
        @Index(name = "customer_notice_customer", columnList = "customerId"),
        @Index(name = "customer_notice_salesforce", columnList = "salesforceId"),
        @Index(name = "customer_notice_sync", columnList = "sync")})
public class CustomerNotice {

    /** Where the console's last change stands with Salesforce. */
    public enum Sync {
        /** Asked for in the console, not written to Salesforce yet. */
        PENDING,
        /** Written to Salesforce; its event, which confirms it, has not come back yet. */
        SENT,
        /** Salesforce has it as the console asked (or nothing was asked). */
        CONFIRMED,
        /** Salesforce refused it: said why, not retried until changed again. */
        FAILED
    }

    @Id
    public String id;
    /** The MDM customer (a survivor: a merge moves its notices). */
    public String customerId;
    /** Its Case in Salesforce, once there is one. */
    public String salesforceId;

    // As Salesforce has it — what the hotels see. Version 0: Salesforce has not confirmed it yet.
    @Column(length = 255)
    public String text;
    /** A {@code CustomerNoticeChanged.NoticeType}'s name. */
    @Column(length = 20)
    public String type;
    public LocalDate fromDate;
    public LocalDate toDate;
    /** {@code CustomerNoticeChanged.NoticeMoment} names, comma separated. */
    @Column(length = 60)
    public String showAt;
    public boolean active;
    /** Grows with every change Salesforce confirms: what the hotels order them by. */
    public long version;
    public Instant confirmedAt;

    // What the console asked for, until Salesforce confirms it.
    @Column(length = 20)
    public String sync;
    @Column(length = 255)
    public String pendingText;
    @Column(length = 20)
    public String pendingType;
    public LocalDate pendingFrom;
    public LocalDate pendingTo;
    @Column(length = 60)
    public String pendingShowAt;
    public Boolean pendingActive;
    public String requestedBy;
    public Instant requestedAt;
    public Instant sentAt;
    @Column(length = 1000)
    public String sendError;

    /** Where it was created: "Salesforce", or "Clientes · <user>". */
    public String origin;

    public boolean pending() {
        return sync != null && !Sync.CONFIRMED.name().equals(sync);
    }
}
