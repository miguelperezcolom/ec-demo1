package io.mateu.ecdemo1.booking.application.usecases.intake;

import io.mateu.ecdemo1.booking.application.out.intake.IntakePauses;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whether the CRS admits bookings — new ones, changes and cancellations, from every entry: the wizard,
 * the API, the agent's tools, the demo bookings. The demo's reset (process reset-demo) pauses it while
 * the services empty their tables, so nothing new lands half-way, and resumes it after.
 *
 * <p>A pause ends by itself, at most {@code booking.intake.max-pause} (30 min) after it began: a reset
 * that dies half-way does not leave the CRS closed. What the engine does to a booking (no-show, the
 * PMS's reference) is not intake, and goes on.
 */
@Service
@Slf4j
public class CrsIntake {

    static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC);

    final IntakePauses pauses;
    final Clock clock;
    final Duration maxPause;

    public CrsIntake(IntakePauses pauses, Clock clock, @Value("${booking.intake.max-pause:30m}") Duration maxPause) {
        this.pauses = pauses;
        this.clock = clock;
        this.maxPause = maxPause;
    }

    public record Status(boolean paused, Instant until, String by, String processKey) {
    }

    public Status status() {
        return pauses.current()
                .filter(p -> p.until() != null && p.until().isAfter(clock.instant()))
                .map(p -> new Status(true, p.until(), p.by(), p.processKey()))
                .orElse(new Status(false, null, null, null));
    }

    /**
     * Pauses it until at most {@code max-pause} from now. The same process pausing it again (a retry)
     * keeps the pause it took: the end does not move.
     */
    @Transactional
    public Instant pause(String processKey, String by) {
        var current = status();
        if (current.paused() && processKey != null && processKey.equals(current.processKey())) {
            return current.until();
        }
        var until = clock.instant().plus(maxPause);
        pauses.save(new IntakePauses.Pause(until, by, processKey));
        log.info("CRS intake paused until {} by {} ({})", until, by, processKey);
        return until;
    }

    /** Admits bookings again. Nothing paused, nothing to do. */
    @Transactional
    public void resume(String processKey) {
        if (pauses.current().isPresent()) {
            pauses.clear();
            log.info("CRS intake resumed ({})", processKey);
        }
    }

    /** @throws IntakePausedException while paused */
    public void ensureOpen() {
        var status = status();
        if (status.paused()) {
            throw new IntakePausedException("El CRS no admite reservas ahora: la demo se está reseteando (hasta "
                    + HH_MM.format(status.until()) + " UTC).");
        }
    }
}
