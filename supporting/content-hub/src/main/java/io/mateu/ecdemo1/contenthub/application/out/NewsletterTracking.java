package io.mateu.ecdemo1.contenthub.application.out;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Opens and clicks of a newsletter, per ISSUE and GROUP of recipients — a group's email goes as one message
 * to all of them, so who in particular opened it is not known (nor asked). (The store's column is still
 * called {@code edition_id}: the issues kept the ids the editions had.)
 */
public interface NewsletterTracking {

    enum Kind { OPEN, CLICK }

    record Event(String issueId, String group, Kind kind, String itemId) {
    }

    /** One group's totals in an issue, and its clicks per item. */
    record GroupStats(String group, long opens, long clicks, Map<String, Long> clicksByItem) {
    }

    /** An issue's totals, for the analytics. */
    record EditionStats(String editionId, String title, long opens, long clicks) {
    }

    /** An issue's totals. */
    record Totals(long opens, long clicks) {
        public static final Totals NONE = new Totals(0, 0);
    }

    void record(Event event);

    /** The issues opened or clicked most recently, oldest of them first. */
    List<EditionStats> recentEditions(int limit);

    List<GroupStats> stats(String issueId);

    /** The totals of some issues (those with none are left out). */
    Map<String, Totals> totals(Collection<String> issueIds);
}
