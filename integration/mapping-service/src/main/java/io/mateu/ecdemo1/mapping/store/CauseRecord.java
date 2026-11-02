package io.mateu.ecdemo1.mapping.store;

import io.mateu.ecdemo1.integration.model.mapping.CauseType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A reason processes are waiting. The operator's unit of work (F012): one cause with N processes
 * behind it is one incident, and resolving it resumes all of them.
 */
@Entity
@Table(name = "cause")
@NoArgsConstructor
@Getter
@Setter
public class CauseRecord {

    @Id
    public String causeKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public CauseType type;
    @Column(length = 1000)
    public String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public CauseStatus status;
    public String hotelCode;
    public Instant openedAt;
    public Instant resolvedAt;
    public String resolvedBy;
    /** How many times it was opened: a cause that keeps coming back says something too. */
    public int openings;
    /**
     * Since when a person lets what waits on it in «on request»: Opera had no room for them, and each
     * goes in by itself as soon as Opera has room for its stay (pms-integration watches). It stays
     * across a resolution and a reopening — it is the person's answer to this refusal at this hotel —
     * until a person withdraws it. Null: nobody chose it.
     */
    public Instant onRequestSince;
    public String onRequestBy;

    /** Whether the refusal it stands for is one a reservation can be let in on request for: no room in Opera. */
    public boolean onRequestable() {
        return type == CauseType.PMS_REJECTED && causeKey != null
                && (causeKey.contains(":upsert-reservation:") || causeKey.endsWith(":upsert-reservation"));
    }
}
