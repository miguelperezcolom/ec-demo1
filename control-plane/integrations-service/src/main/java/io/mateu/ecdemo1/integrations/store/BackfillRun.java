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

import java.time.Instant;
import java.time.LocalDate;

/**
 * One backfill of a hotel (HLA «Backfill» #10): its future reservations projected nearest arrival
 * first, a batch at a time. The cursor — the last one projected — is what makes it resumable: a
 * restart picks up after it, and projecting one twice is harmless anyway.
 */
@Entity
@Table(name = "backfill_run")
@NoArgsConstructor
@Getter
@Setter
public class BackfillRun {

    public enum Status { RUNNING, COMPLETED, STOPPED }

    @Id
    public String id;
    @Column(nullable = false)
    public String integrationId;
    @Column(nullable = false)
    public String crsHotelCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Status status;
    /** Part of the onboarding — its end is a gate — or relaunched on demand (F013). */
    public boolean onboarding;

    public LocalDate cursorArrival;
    public String cursorLocator;
    public int dispatched;
    public Integer expected;
    /** Arrivals up to here make the «ventana próxima» (R25): once the cursor is past it, the hotel can be activated. */
    public LocalDate windowEnd;
    public boolean windowCovered;

    public Instant startedAt;
    public String startedBy;
    public Instant finishedAt;
    public Instant lastTickAt;
}
