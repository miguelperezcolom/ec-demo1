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
        @Index(name = "audit_record_actor", columnList = "actor"),
        @Index(name = "audit_record_stay", columnList = "stayId"),
        @Index(name = "audit_record_locator", columnList = "locator")})
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
    /**
     * The stay and the CRS locator the action was on, when its parameters say — the front office's and
     * the CRS's actions do: what a reservation's history is looked up by. Read from the parameters as the
     * action arrives; null for an action on no reservation.
     */
    public String stayId;
    public String locator;

    static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

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
        r.stayId = parameter(a.parameters(), "stayId");
        r.locator = parameter(a.parameters(), "locator");
        return r;
    }

    /** A top-level text parameter of the action's JSON parameters; null if there is none or they are not JSON. */
    static String parameter(String parameters, String name) {
        if (parameters == null || parameters.isBlank()) {
            return null;
        }
        try {
            var node = JSON.readTree(parameters).get(name);
            return node == null || node.isNull() || !node.isValueNode() ? null : node.asText();
        } catch (Exception e) {
            return null;
        }
    }
}
