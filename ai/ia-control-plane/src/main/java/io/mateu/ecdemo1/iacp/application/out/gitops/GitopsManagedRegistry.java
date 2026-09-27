package io.mateu.ecdemo1.iacp.application.out.gitops;

import java.util.Set;

/**
 * Remembers which catalogue entries git created, so the reconciler can tell them from the ones a
 * person made in the console. This is the whole of the provenance model: git owns what is in here,
 * the console owns what is not.
 *
 * <p>It is a register of {@code (kind, id)} pairs and nothing more — no copy of the entry, no
 * version. It answers exactly two questions the reconciler asks: "did git create this?" (so a
 * console entry with a colliding id is never overwritten) and "which ids does git own?" (so one
 * removed from the repo can be cleaned up, while a console entry with no repo file is left alone).
 */
public interface GitopsManagedRegistry {

    /** Whether git created the entry {@code (kind, id)}. False for console-made and unknown ids. */
    boolean isManaged(String kind, String id);

    /** Every id of {@code kind} that git owns — the set a reconcile checks for removals against. */
    Set<String> managedIds(String kind);

    /** Record that git now owns {@code (kind, id)}. Idempotent. */
    void markManaged(String kind, String id);

    /** Forget {@code (kind, id)} — called when git no longer declares it and it has been removed. */
    void unmark(String kind, String id);
}
