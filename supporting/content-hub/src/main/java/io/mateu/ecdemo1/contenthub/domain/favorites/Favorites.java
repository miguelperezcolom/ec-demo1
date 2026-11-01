package io.mateu.ecdemo1.contenthub.domain.favorites;

import io.mateu.ecdemo1.contenthub.domain.shared.AggregateRoot;

import java.util.ArrayList;
import java.util.List;

/**
 * One employee's favourites, by item id — apart from the employee, so a directory update never competes
 * with someone starring an article. An item that stops being visible is hidden from "Mis favoritos" by
 * the visibility filter, not removed here: it comes back if it becomes visible again.
 */
public class Favorites extends AggregateRoot {

    private String userId;
    private List<String> itemIds;

    @SuppressWarnings("unused") // for the store
    private Favorites() {
    }

    public static Favorites of(String userId) {
        var f = new Favorites();
        f.userId = userId;
        f.itemIds = new ArrayList<>();
        return f;
    }

    public void add(String itemId) {
        if (!itemIds.contains(itemId)) {
            itemIds.addFirst(itemId);
        }
    }

    public void remove(String itemId) {
        itemIds.remove(itemId);
    }

    public boolean contains(String itemId) {
        return itemIds.contains(itemId);
    }

    public String userId() {
        return userId;
    }

    /** Most recently added first. */
    public List<String> itemIds() {
        return List.copyOf(itemIds);
    }
}
