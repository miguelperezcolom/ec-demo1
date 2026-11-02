package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.uisource.UiSource;

/** The registry, writable: only {@code UiSourceCommands} (content:admin) writes it. */
public interface UiSourceRepository extends UiSources {

    void save(UiSource source);

    void delete(String id);
}
