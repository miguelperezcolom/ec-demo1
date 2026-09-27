package io.mateu.ecdemo1.journey.model;

/**
 * One thing that happened to the booking in one system, in business words.
 *
 * @param startNanos when it started, epoch nanoseconds; 0 when the traces do not say (Salesforce)
 * @param endNanos   when it ended
 * @param link       where to see it, or null
 */
public record Hop(Lane lane, long startNanos, long endNanos, String title, String detail, Tone tone, String link) {

    public boolean timed() {
        return startNanos > 0;
    }

    public long durationMillis() {
        return timed() ? Math.max(0, (endNanos - startNanos) / 1_000_000) : 0;
    }

    public Hop withDetail(String detail) {
        return new Hop(lane, startNanos, endNanos, title, detail, tone, link);
    }

    public Hop withTone(Tone tone) {
        return new Hop(lane, startNanos, endNanos, title, detail, tone, link);
    }
}
