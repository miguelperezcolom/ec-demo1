package io.mateu.ecdemo1.operamock.store;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Failures on demand: the next N calls whose path contains a given text answer with a given
 * status. How the adapter's handling of an outage, a rate limit or a timeout gets exercised
 * without waiting for Opera to have one.
 */
@Component
public class Faults {

    public record Fault(int status, String pathContains, int remaining) {
    }

    final AtomicReference<Fault> current = new AtomicReference<>();
    final AtomicInteger injected = new AtomicInteger();

    public void inject(int status, String pathContains, int count) {
        current.set(count > 0 ? new Fault(status, pathContains == null ? "" : pathContains, count) : null);
    }

    /** The status this call must fail with, if a fault applies to it; consumes one. */
    public Integer take(String path) {
        while (true) {
            var fault = current.get();
            if (fault == null || !path.contains(fault.pathContains())) {
                return null;
            }
            var next = fault.remaining() > 1 ? new Fault(fault.status(), fault.pathContains(), fault.remaining() - 1) : null;
            if (current.compareAndSet(fault, next)) {
                injected.incrementAndGet();
                return fault.status();
            }
        }
    }

    public Fault current() {
        return current.get();
    }

    public int injected() {
        return injected.get();
    }
}
