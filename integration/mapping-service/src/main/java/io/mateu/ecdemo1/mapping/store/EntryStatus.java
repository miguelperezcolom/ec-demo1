package io.mateu.ecdemo1.mapping.store;

/**
 * An entry of the dictionary is proposed — by the agent or by a person — and then decided. Only an
 * APPROVED entry translates anything; approving a new version SUPERSEDES the previous one, which is
 * kept, with its author, as the history of that code.
 */
public enum EntryStatus {
    PROPOSED, APPROVED, REJECTED, SUPERSEDED,
    /** Was in force and was taken out of it, with no replacement: the code has no equivalence again. */
    WITHDRAWN
}
