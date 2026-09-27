package io.mateu.ecdemo1.integrations.store;

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
import java.util.ArrayList;
import java.util.List;

/**
 * One backfill of a front office: the PMS property's reservations in the horizon, read once when it
 * starts, projected a batch per tick. What is left is {@link #pending}: a restart goes on from it, and
 * projecting one twice is harmless — the process's key is the reservation's version.
 */
@Entity
@Table(name = "fo_backfill_run")
@NoArgsConstructor
@Getter
@Setter
public class FoBackfillRun {

    public enum Status { RUNNING, COMPLETED, STOPPED }

    /** One reservation still to project, with the PMS's version it was read at. */
    public record Item(String pmsReservationId, String lastModified) {
    }

    @Id
    public String id;
    @Column(nullable = false)
    public String integrationId;
    @Column(nullable = false)
    public String pmsHotelCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Status status;
    public boolean onboarding;

    @JdbcTypeCode(SqlTypes.JSON)
    public List<Item> pending = new ArrayList<>();
    public int dispatched;
    public Integer expected;
    /** The latest PMS modification among what was read: the polling starts from it. */
    public String cursorAtStart;

    public Instant startedAt;
    public String startedBy;
    public Instant finishedAt;
    public Instant lastTickAt;
}
