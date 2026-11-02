package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.preview.PreviewLink;

import java.util.List;
import java.util.Optional;

public interface PreviewLinkRepository {

    Optional<PreviewLink> find(String id);

    void save(PreviewLink link);

    /** An item's links, the newest first. */
    List<PreviewLink> ofItem(String itemId);

    /** Every link, the newest first. */
    List<PreviewLink> findAll();

    /**
     * The key the links are signed with when none is configured: made once, at random, and kept — a link
     * lasts days, and must outlive a restart.
     */
    String signingKey();
}
