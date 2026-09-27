package io.mateu.ecdemo1.audit.store;

import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * An audited action as it arrived. Immutable: nothing in the application changes or deletes it — an
 * audit trail that can be edited is not one (HLA F016).
 */
@Entity
@Immutable
@Table(name = "audit_record", indexes = {
        @Index(name = "audit_record_at", columnList = "at"),
        @Index(name = "audit_record_hotel", columnList = "hotelCode"),
        @Index(name = "audit_record_actor", columnList = "actor")})
public class AuditRecord {

    @Id
    public String actionId;
    public Instant at;
    public String service;
    public String action;
    public String hotelCode;
    /** Who: "by" is not a column name PostgreSQL accepts unquoted. */
    public String actor;
    @Column(columnDefinition = "text")
    public String parameters;
    public boolean succeeded;
    @Column(columnDefinition = "text")
    public String response;
    public Instant receivedAt;

    public static AuditRecord of(AuditedAction a, Instant receivedAt) {
        var r = new AuditRecord();
        r.actionId = a.actionId();
        r.at = a.at();
        r.service = a.service();
        r.action = a.action();
        r.hotelCode = a.hotelCode();
        r.actor = a.by();
        r.parameters = a.parameters();
        r.succeeded = a.succeeded();
        r.response = a.response();
        r.receivedAt = receivedAt;
        return r;
    }
}
