package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.collaboration.Discussion;

import java.util.List;

/** The comments on each item. */
public interface DiscussionRepository {

    /** The item's discussion; an empty, open one when there is none yet. */
    Discussion get(String itemId);

    /**
     * The same, to change it: holds the item's discussion until the transaction ends, so that two
     * comments at the same moment do not overwrite each other in the one document.
     */
    Discussion lockAndGet(String itemId);

    void save(Discussion discussion);

    /** Every discussion with at least one comment: for the moderators. */
    List<Discussion> withComments();
}
