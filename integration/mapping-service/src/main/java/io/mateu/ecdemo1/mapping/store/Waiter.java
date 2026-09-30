package io.mateu.ecdemo1.mapping.store;

import io.mateu.workflow.dtos.Variable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/**
 * A process waiting for its causes. It keeps what it takes to start the process's successor —
 * references, never the reservation — and nothing else.
 */
@Entity
@Table(name = "waiter")
@NoArgsConstructor
@Getter
@Setter
public class Waiter {

    @Id
    public String processKey;
    @Column(nullable = false)
    public String definitionId;
    public String hotelCode;
    /** The reservation's locator, or the partner's code. */
    public String subject;
    @JdbcTypeCode(SqlTypes.JSON)
    public List<Variable> variables;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public WaiterStatus status;
    public Instant createdAt;
    public Instant releasedAt;
    public Instant lastSignalAt;
    public Instant finishedAt;
    public String finishedBy;
    /**
     * The engine's id of the waiting process — what the engine cancels a process by. Recorded when the
     * wait is registered from the engine's task; null for waits registered before it was kept, or over
     * REST without it: those are cancelled by hand, in Admin → Processes.
     */
    public String engineProcessId;
    /** Why a person discarded it (F012): asked in the discard dialog, kept with who and when. */
    @Column(length = 1000)
    public String reason;
}
