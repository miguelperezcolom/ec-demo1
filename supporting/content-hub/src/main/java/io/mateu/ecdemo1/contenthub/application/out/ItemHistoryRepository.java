package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.item.HistoryEntry;

import java.util.List;

/** Every item's history, one row per step; it is only appended to. */
public interface ItemHistoryRepository {

    /** Stores the entry with the next number of its item, and returns it numbered. */
    HistoryEntry append(HistoryEntry entry);

    /** An item's steps, oldest first. */
    List<HistoryEntry> of(String itemId);
}
