package io.mateu.ecdemo1.mdm.salesforce;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * When a job that calls Salesforce may try again after failing: at once while it goes well, then
 * after a wait that doubles with each failure in a row, up to a longest one. A scheduled job ticking
 * every few seconds against a Salesforce that errors would otherwise spend the org's daily allowance
 * on errors.
 */
public class Backoff {

    final Clock clock;
    final Duration first;
    final Duration longest;
    Duration wait;
    Instant next;
    int failures;

    public Backoff(Clock clock, Duration first, Duration longest) {
        this.clock = clock;
        this.first = first;
        this.longest = longest;
        this.wait = first;
    }

    /** Whether it may try now. */
    public synchronized boolean ready() {
        return next == null || !clock.instant().isBefore(next);
    }

    public synchronized void failed() {
        failures++;
        next = clock.instant().plus(wait);
        wait = wait.multipliedBy(2).compareTo(longest) > 0 ? longest : wait.multipliedBy(2);
    }

    public synchronized void succeeded() {
        failures = 0;
        next = null;
        wait = first;
    }

    public synchronized int failures() {
        return failures;
    }

    public synchronized Instant next() {
        return next;
    }
}
