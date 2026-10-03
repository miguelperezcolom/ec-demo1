package io.mateu.ecdemo1.booking.application.out.intake;

import java.time.Instant;
import java.util.Optional;

/** Where the CRS keeps whether it admits bookings: one pause at most, which outlives a restart. */
public interface IntakePauses {

    record Pause(Instant until, String by, String processKey) {
    }

    Optional<Pause> current();

    void save(Pause pause);

    void clear();
}
