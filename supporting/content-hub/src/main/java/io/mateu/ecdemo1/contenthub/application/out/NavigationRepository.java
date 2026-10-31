package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.navigation.Navigation;

public interface NavigationRepository {

    /** The tree; an empty one when none was saved yet. */
    Navigation get();

    void save(Navigation navigation);
}
