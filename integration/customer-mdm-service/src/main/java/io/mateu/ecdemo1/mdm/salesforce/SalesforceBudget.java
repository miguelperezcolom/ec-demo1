package io.mateu.ecdemo1.mdm.salesforce;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The org's daily API allowance, as the MDM sees it. Salesforce counts every REST and SOAP call in a
 * rolling 24 hours (a Base Edition has 15,000 of them) and, past it, answers {@code REQUEST_LIMIT_EXCEEDED}
 * to everything until enough of the day's calls have rolled out of the window.
 *
 * <p>Asking again every few seconds only spends what is coming back. So the first refusal pauses every
 * call for a while — five minutes, doubled on each refusal after that, up to an hour — and whatever
 * would have called waits: projections stay pending, polls skip, merges wait. Only one call, the first
 * after the pause, finds out whether the org answers again. The episode is announced once when it
 * starts and closed once when a call succeeds, not logged every minute.
 *
 * <p>It also keeps the last {@code Sforce-Limit-Info} header Salesforce sent ({@code api-usage=used/max}),
 * so the usage is known without spending a call on the limits resource.
 */
@Slf4j
public class SalesforceBudget {

    static final Pattern USAGE = Pattern.compile("(?<![-\\w])api-usage=(\\d+)/(\\d+)");

    /** What a pause is: from when, until when, and why Salesforce said so. */
    public record Pause(Instant since, Instant until, String reason) {
    }

    /** Told once when an episode starts ({@code Pause}), and once when it ends (null). */
    public interface Listener {
        void paused(Pause pause);

        void resumed(Pause pause);
    }

    final Clock clock;
    final Duration first;
    final Duration longest;
    volatile Listener listener = new Listener() {
        public void paused(Pause pause) {
        }

        public void resumed(Pause pause) {
        }
    };

    Instant episodeSince;
    Instant pausedUntil;
    Duration nextPause;
    String reason;
    volatile long used = -1;
    volatile long max = -1;

    public SalesforceBudget(Clock clock, Duration first, Duration longest) {
        this.clock = clock;
        this.first = first;
        this.longest = longest;
        this.nextPause = first;
    }

    public void listener(Listener listener) {
        this.listener = listener;
    }

    /** Whether a call may go now: not while paused. */
    public synchronized boolean open() {
        return pausedUntil == null || !clock.instant().isBefore(pausedUntil);
    }

    /** When the pause ends, if there is one now. */
    public synchronized Instant pausedUntil() {
        return open() ? null : pausedUntil;
    }

    /** Salesforce refused a call for the allowance: pause, longer each time it happens again. */
    public void exceeded(String why) {
        Pause started = null;
        synchronized (this) {
            var now = clock.instant();
            if (!open()) {
                return; // a call that was already in flight when the pause began
            }
            pausedUntil = now.plus(nextPause);
            nextPause = min(nextPause.multipliedBy(2), longest);
            reason = why;
            if (episodeSince == null) {
                episodeSince = now;
                started = new Pause(episodeSince, pausedUntil, why);
                log.warn("Salesforce's daily API allowance is spent ({}): calls paused until {}", usage(), pausedUntil);
            } else {
                log.info("Salesforce still refuses for the allowance: calls paused until {}", pausedUntil);
            }
        }
        if (started != null) {
            var pause = started;
            notify(l -> l.paused(pause));
        }
    }

    /** A call went through: the episode, if there was one, is over. */
    public void succeeded() {
        Pause ended = null;
        synchronized (this) {
            if (episodeSince != null) {
                ended = new Pause(episodeSince, clock.instant(), reason);
                log.info("Salesforce answers again ({}), after refusing since {}", usage(), episodeSince);
            }
            episodeSince = null;
            pausedUntil = null;
            nextPause = first;
            reason = null;
        }
        if (ended != null) {
            var pause = ended;
            notify(l -> l.resumed(pause));
        }
    }

    /** What a response's {@code Sforce-Limit-Info} header says: {@code api-usage=14438/15000}. */
    public void observe(String limitInfo) {
        if (limitInfo == null) {
            return;
        }
        var m = USAGE.matcher(limitInfo);
        if (m.find()) {
            used = Long.parseLong(m.group(1));
            max = Long.parseLong(m.group(2));
        }
    }

    /** Calls left in the rolling 24 hours as last seen, or -1 when none has been seen yet. */
    public long remaining() {
        return max < 0 ? -1 : Math.max(0, max - used);
    }

    public String usage() {
        return max < 0 ? "usage not seen yet" : "api-usage " + used + "/" + max;
    }

    /** Whether Salesforce's answer is the allowance refusing: REST's error code, or SOAP's fault. */
    public static boolean isLimit(String answer) {
        return answer != null && answer.contains("REQUEST_LIMIT_EXCEEDED");
    }

    void notify(Consumer<Listener> call) {
        try {
            call.accept(listener);
        } catch (RuntimeException e) {
            log.warn("Could not announce Salesforce's allowance state: {}", e.getMessage());
        }
    }

    static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
