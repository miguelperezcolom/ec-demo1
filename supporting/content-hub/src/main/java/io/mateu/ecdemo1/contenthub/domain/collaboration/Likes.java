package io.mateu.ecdemo1.contenthub.domain.collaboration;

import io.mateu.ecdemo1.contenthub.domain.shared.AggregateRoot;
import io.mateu.ecdemo1.contenthub.domain.shared.CollaborationEvents;

import java.util.ArrayList;
import java.util.List;

/** The «Me gusta» of one item: at most one per reader, toggled. */
public class Likes extends AggregateRoot {

    private String itemId;
    private List<String> userIds;

    @SuppressWarnings("unused") // for the store
    private Likes() {
    }

    public static Likes of(String itemId) {
        var l = new Likes();
        l.itemId = itemId;
        l.userIds = new ArrayList<>();
        return l;
    }

    /** @return whether the reader likes it now */
    public boolean toggle(String userId) {
        if (userIds.remove(userId)) {
            return false;
        }
        userIds.add(userId);
        raise(new CollaborationEvents.ItemLiked(itemId, userIds.size()));
        return true;
    }

    public boolean likedBy(String userId) {
        return userIds.contains(userId);
    }

    public int count() {
        return userIds.size();
    }

    public String itemId() {
        return itemId;
    }
}
