package io.mateu.ecdemo1.pmsintegration.worker;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Serialises, per reservation, the read of the version Opera holds and the write that follows it.
 * OHIP offers no conditional write, so without this two steps for the same reservation could both
 * read version 2 and the slower one — carrying the older state — would win (HLA, «El orden de
 * aplicación»).
 *
 * <p>In memory, so it holds for one replica of this service, which is how it is deployed. With more
 * than one it has to become the engine's LOCK step around the write — which is where the HLA puts
 * it, and where it was until the engine's LOCK turned out to fail on PostgreSQL (2.18.0: its key
 * carries a NUL character, which PostgreSQL does not accept in text).
 *
 * <p>Striped: a fixed set of locks shared by hash, so the memory is bounded whatever the number of
 * reservations; two reservations that share a stripe only wait for each other, never interleave.
 */
@Component
public class ReservationLocks {

    static final int STRIPES = 256;

    final ReentrantLock[] stripes = new ReentrantLock[STRIPES];

    public ReservationLocks() {
        for (int i = 0; i < STRIPES; i++) {
            stripes[i] = new ReentrantLock();
        }
    }

    public <T> T withLock(String hotelCode, String locator, Supplier<T> action) {
        var lock = stripes[Math.floorMod((hotelCode + "/" + locator).hashCode(), STRIPES)];
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
