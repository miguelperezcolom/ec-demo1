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
}
