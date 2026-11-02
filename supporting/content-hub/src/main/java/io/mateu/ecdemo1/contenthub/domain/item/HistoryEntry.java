package io.mateu.ecdemo1.contenthub.domain.item;

import java.time.Instant;

/**
 * One step of an item's history, apart from the item: it only grows, and thousands of saves must not
 * weigh on the aggregate every editor loads.
 *
 * @param seq      its number within the item (1, 2, …), given by the store when appended; 0 for a step
 *                 read off the item itself (a publication recorded before the history existed)
 * @param version  PUBLISHED: the version it made
 * @param note     RESTORED: what was restored («versión 2», «#5»)
 * @param snapshot the draft as it was left by the step — CREATED, EDITED, RESTORED; null for the rest
 *                 (a publication's content is its {@link ItemVersion})
 */
public record HistoryEntry(String itemId, int seq, HistoryAction action, Instant at, String by, Integer version,
                           String note, ItemContent snapshot) {

    public static HistoryEntry of(String itemId, HistoryAction action, Instant at, String by, Integer version,
                                  String note, ItemContent snapshot) {
        return new HistoryEntry(itemId, 0, action, at, by, version, note, snapshot);
    }

    public HistoryEntry withSeq(int seq) {
        return new HistoryEntry(itemId, seq, action, at, by, version, note, snapshot);
    }
}
