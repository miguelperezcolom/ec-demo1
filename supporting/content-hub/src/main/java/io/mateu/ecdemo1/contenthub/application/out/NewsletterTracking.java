package io.mateu.ecdemo1.contenthub.application.out;

import java.util.List;

/**
 * Opens and clicks of a newsletter, per edition and GROUP of recipients — a group's email goes as one
 * message to all of them, so who in particular opened it is not known (nor asked).
 */
public interface NewsletterTracking {

    enum Kind { OPEN, CLICK }

    record Event(String editionId, String group, Kind kind, String itemId) {
    }

    /** One group's totals in an edition, and its clicks per item. */
    record GroupStats(String group, long opens, long clicks, java.util.Map<String, Long> clicksByItem) {
    }

    void record(Event event);

    List<GroupStats> stats(String editionId);
}
