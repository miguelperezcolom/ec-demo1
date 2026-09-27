package io.mateu.ecdemo1.mapping.dictionary;

import io.mateu.ecdemo1.mapping.store.MappingEntry;

/**
 * Another decision on the same code and scope was taken at the same moment and won: this one
 * changed nothing. It is a conflict (409 through the API), not a failure — looking at the
 * dictionary again shows what is in force, and approving again replaces it if that is still wanted.
 */
public class ConcurrentDecisionException extends IllegalStateException {

    public ConcurrentDecisionException(MappingEntry entry, Throwable cause) {
        super("Another version of " + entry.describe() + " was approved at the same moment, and this approval"
                + " changed nothing. Look at the dictionary again: approve this one again if it should replace it.", cause);
    }
}
