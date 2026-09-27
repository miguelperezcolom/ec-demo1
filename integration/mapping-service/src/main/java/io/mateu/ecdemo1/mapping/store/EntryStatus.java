package io.mateu.ecdemo1.mapping.store;

import java.util.Set;

/**
 * An entry of the dictionary is proposed — by the agent or by a person — and then decided. Only an
 * APPROVED entry translates anything; approving a new version SUPERSEDES the previous one, which is
 * kept, with its author, as the history of that code.
 *
 * <pre>
 *   PROPOSED ──approve──▶ APPROVED ──(a newer version is approved)──▶ SUPERSEDED
 *      │                     └──withdraw──▶ WITHDRAWN
 *      └──reject──▶ REJECTED
 * </pre>
 *
 * REJECTED, SUPERSEDED and WITHDRAWN are final: the history is never rewritten.
 */
public enum EntryStatus {
    PROPOSED, APPROVED, REJECTED, SUPERSEDED,
    /** Was in force and was taken out of it, with no replacement: the code has no equivalence again. */
    WITHDRAWN;

    /** The states this one can move to. */
    public Set<EntryStatus> next() {
        return switch (this) {
            case PROPOSED -> Set.of(APPROVED, REJECTED);
            case APPROVED -> Set.of(SUPERSEDED, WITHDRAWN);
            case REJECTED, SUPERSEDED, WITHDRAWN -> Set.of();
        };
    }

    public boolean canBecome(EntryStatus next) {
        return next().contains(next);
    }
}
