package io.mateu.ecdemo1.contenthub.application.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The intranet's usage: raw events for a while, and what they add up to per day for good. */
public interface UsageRepository {

    enum Kind { ITEM_VIEW, SEARCH, HOME_VISIT }

    /** The daily aggregates' metrics: a total under the key "", or a figure per key. */
    final class Metric {
        public static final String VIEWS = "views";
        public static final String VIEWS_BY_ITEM = "views.item";
        public static final String SEARCHES = "searches";
        public static final String SEARCHES_EMPTY = "searches.empty";
        public static final String SEARCHES_BY_QUERY = "searches.query";
        public static final String SEARCHES_EMPTY_BY_QUERY = "searches.empty.query";
        public static final String HOME_VISITS = "home";
        public static final String READERS = "readers";

        private Metric() {
        }
    }

    /**
     * @param userId       who, for views and home visits; a search keeps none
     * @param profileHash  whose kind of reader
     * @param query        what was searched, normalised
     * @param results      how many it found
     */
    record Event(Kind kind, Instant at, String userId, String profileHash, String itemId, String query, Integer results) {
    }

    record Ranked(String key, long value) {
    }

    /** An item's figures since it exists. */
    record ItemStats(long views, long uniqueReaders) {
    }

    void record(Event event);

    Optional<LocalDate> lastAggregatedDay();

    Optional<Instant> firstEvent();

    /** Replaces the day's aggregates with what the raw events in [from, to) add up to. */
    void aggregate(LocalDate day, Instant from, Instant to);

    /** @return how many raw events went */
    int purgeBefore(Instant at);

    Set<String> activeReaders(Instant since);

    long total(String metric, LocalDate from);

    Map<LocalDate, Long> perDay(String metric, LocalDate from);

    List<Ranked> top(String metric, LocalDate from, int limit);

    ItemStats itemStats(String itemId);
}
