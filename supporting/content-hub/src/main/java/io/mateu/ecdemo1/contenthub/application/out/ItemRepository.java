package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.item.Item;

import java.util.List;
import java.util.Optional;

public interface ItemRepository {

    Optional<Item> find(String id);

    void save(Item item);

    List<Item> findAll();

    /** The backoffice's list: by title, type or status. */
    List<ItemSummary> search(String text, int offset, int limit);

    long count(String text);

    record ItemSummary(String id, String type, String status, String title, String author, String updatedAt,
                       boolean pendingChanges, boolean inReview) {
    }
}
