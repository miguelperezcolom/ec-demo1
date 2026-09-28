package io.mateu.ecdemo1.integration.model.usage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * The calls a service made to an external API, by what they were for and how they went, in hourly
 * buckets over the last 24 hours — the window Salesforce counts its daily allowance in, and the
 * "today" of the consoles' header. What {@link ApiUsage#ours24h()} and its breakdowns are made of,
 * the same for every service that calls Salesforce or Opera.
 *
 * <p>Plain Java: each service also hands every call to its metrics ({@code onRecord}) — the counter
 * Grafana and the alerts read — and may save the buckets to survive a restart ({@link #live()},
 * {@link #restore(List)}).
 */
public class ApiCalls {

    /** How a call went. */
    public enum Outcome {
        OK, ERROR, LIMITED;

        /** As a metric tag: {@code ok}, {@code error}, {@code limited}. */
        public String tag() {
            return name().toLowerCase();
        }
    }

    /** One hour's count of one purpose and outcome. */
    public record Bucket(Instant hour, String purpose, Outcome outcome, long calls) {
    }

    record Key(long slot, String purpose, Outcome outcome) {
    }

    public static final Duration WINDOW = Duration.ofHours(24);

    final Clock clock;
    final BiConsumer<String, Outcome> onRecord;
    final ConcurrentHashMap<Key, AtomicLong> buckets = new ConcurrentHashMap<>();
    /** The last two hours by the minute and purpose: the trend. In memory only — a restart starts it again. */
    final ConcurrentHashMap<Key, AtomicLong> minutes = new ConcurrentHashMap<>();

    /** @param onRecord told of every call as it is counted — the service's metrics; may be null */
    public ApiCalls(Clock clock, BiConsumer<String, Outcome> onRecord) {
        this.clock = clock;
        this.onRecord = onRecord == null ? (p, o) -> {
        } : onRecord;
    }

    public void record(String purpose, Outcome outcome) {
        var now = clock.instant();
        buckets.computeIfAbsent(new Key(hourOf(now), purpose, outcome), k -> new AtomicLong()).incrementAndGet();
        minutes.computeIfAbsent(new Key(minuteOf(now), purpose, outcome), k -> new AtomicLong()).incrementAndGet();
        onRecord.accept(purpose, outcome);
    }

    /** The calls of the last 60 minutes, but for those of the purposes named. */
    public long lastHour(String... except) {
        var now = minuteOf(clock.instant());
        return inMinutes(now - 59, now, except);
    }

    /** The calls of the 60 minutes before the last 60: with {@link #lastHour}, whether it speeds up. */
    public long previousHour(String... except) {
        var now = minuteOf(clock.instant());
        return inMinutes(now - 119, now - 60, except);
    }

    long inMinutes(long from, long to, String... except) {
        minutes.keySet().removeIf(k -> k.slot() < from - 60);
        var excluded = List.of(except);
        return minutes.entrySet().stream()
                .filter(e -> e.getKey().slot() >= from && e.getKey().slot() <= to && !excluded.contains(e.getKey().purpose()))
                .mapToLong(e -> e.getValue().get()).sum();
    }

    static long minuteOf(Instant at) {
        return Math.floorDiv(at.getEpochSecond(), 60);
    }

    /** The calls of the last 24 hours by purpose, whatever their outcome, the busiest first. */
    public Map<String, Long> byPurpose() {
        var by = new TreeMap<String, Long>();
        live().forEach(b -> by.merge(b.purpose(), b.calls(), Long::sum));
        return sortedByCount(by);
    }

    /** The calls of the last 24 hours, but for those of the purposes named (a token is not a call Salesforce counts). */
    public long total(String... except) {
        var excluded = List.of(except);
        return live().stream().filter(b -> !excluded.contains(b.purpose())).mapToLong(Bucket::calls).sum();
    }

    public long total(Outcome outcome) {
        return live().stream().filter(b -> b.outcome() == outcome).mapToLong(Bucket::calls).sum();
    }

    /** Every bucket still inside the window, oldest first; older ones are dropped on the way. */
    public List<Bucket> live() {
        var oldest = hourOf(clock.instant().minus(WINDOW)) + 1;
        buckets.keySet().removeIf(k -> k.slot() < oldest);
        return buckets.entrySet().stream()
                .map(e -> new Bucket(Instant.ofEpochSecond(e.getKey().slot() * 3600), e.getKey().purpose(),
                        e.getKey().outcome(), e.getValue().get()))
                .sorted(Comparator.comparing(Bucket::hour).thenComparing(Bucket::purpose))
                .toList();
    }

    /** Puts back what an earlier run counted; a bucket counted here already keeps the larger count. */
    public void restore(List<Bucket> saved) {
        for (var b : saved) {
            buckets.computeIfAbsent(new Key(hourOf(b.hour()), b.purpose(), b.outcome()), k -> new AtomicLong())
                    .accumulateAndGet(b.calls(), Math::max);
        }
    }

    static long hourOf(Instant at) {
        return Math.floorDiv(at.getEpochSecond(), 3600);
    }

    /** The counts, the largest first (ties by name): how a breakdown reads. */
    public static Map<String, Long> sortedByCount(Map<String, Long> counts) {
        var sorted = new LinkedHashMap<String, Long>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }
}
