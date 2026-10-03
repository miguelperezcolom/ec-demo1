package io.mateu.ecdemo1.booking.infra.out.intake;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The CRS's intake pause, one row at most. Not in the demo reset's plan: the reset runs while it is
 * there, and must not take it away.
 */
@Entity
@Table(name = "crs_intake_pause")
@NoArgsConstructor
@Getter
@Setter
public class IntakePauseEntity {

    public static final String ID = "crs";

    @Id
    String id;

    Instant pausedUntil;

    String pausedBy;

    String processKey;
}
