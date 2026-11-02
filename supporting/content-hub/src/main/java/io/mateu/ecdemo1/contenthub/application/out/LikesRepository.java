package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.collaboration.Likes;

/** The «Me gusta» of each item. */
public interface LikesRepository {

    Likes get(String itemId);

    /** The same, to change it: holds the item's likes until the transaction ends (see DiscussionRepository). */
    Likes lockAndGet(String itemId);

    void save(Likes likes);
}
