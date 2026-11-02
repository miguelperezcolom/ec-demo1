package io.mateu.ecdemo1.contenthub.application.out;

import java.util.List;

/**
 * Which pages are homes, in order: a reader's home is the first of them they can see (and the
 * portal's default home if none). The pages themselves are ordinary items with their own audience.
 */
public interface HomePages {

    /** The intranet's homes. */
    default List<String> pageIds() {
        return pageIds(io.mateu.ecdemo1.contenthub.domain.site.Site.INTRANET);
    }

    default void save(List<String> pageIds) {
        save(io.mateu.ecdemo1.contenthub.domain.site.Site.INTRANET, pageIds);
    }

    /** A site's homes. */
    List<String> pageIds(String siteId);

    void save(String siteId, List<String> pageIds);
}
