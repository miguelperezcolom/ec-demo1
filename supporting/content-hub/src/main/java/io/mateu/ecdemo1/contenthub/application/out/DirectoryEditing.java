package io.mateu.ecdemo1.contenthub.application.out;

import java.util.Optional;
import java.util.Set;

/**
 * Where a person's data is edited, for who may. This service only reads the directory: another owner of the
 * employees means another link here, or none, and nothing else changes.
 */
public interface DirectoryEditing {

    /** The link to edit {@code userId} for someone holding {@code scopes}; empty if they may not, or it can't be done from here. */
    Optional<String> linkFor(Set<String> scopes, String userId);
}
