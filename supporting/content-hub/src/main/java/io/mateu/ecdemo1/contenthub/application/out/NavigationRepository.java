package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.navigation.Navigation;

public interface NavigationRepository {

    /** The intranet's tree; an empty one when none was saved yet. */
    default Navigation get() {
        return get(io.mateu.ecdemo1.contenthub.domain.site.Site.INTRANET);
    }

    default void save(Navigation navigation) {
        save(io.mateu.ecdemo1.contenthub.domain.site.Site.INTRANET, navigation);
    }

    /** A site's tree; an empty one when none was saved yet. */
    Navigation get(String siteId);

    void save(String siteId, Navigation navigation);
}
