package io.mateu.ecdemo1.users.application.out.outbox;

import io.mateu.ecdemo1.users.application.out.identity.UserIdentity;

import java.time.Duration;
import java.util.List;

/**
 * The transactional outbox for identity changes: the seam that lets "a user was saved" and "the
 * identity provider was told" be one atomic fact without a distributed transaction.
 *
 * <p>The problem it solves: saving the user and calling Keycloak cannot both be committed together
 * — one is a database, the other an HTTP API — so doing them in sequence leaves a window where the
 * first succeeds and the second does not, and the two drift apart with nothing recording that they
 * did. The outbox closes the window by writing the intent to send into the <em>same</em> database
 * transaction as the user ({@link #append}). Either both land or neither does. A separate relay
 * then reads what landed and delivers it, retrying until it sticks.
 *
 * <p>So delivery is at-least-once, never at-most-once: the relay may hand the same change over
 * twice (it delivered, then died before recording that it had), and that is why
 * {@link io.mateu.ecdemo1.users.application.out.identity.IdentityProviderPort#upsertUser} has to be
 * idempotent. The one guarantee the outbox will not give is exactly-once, because nothing spanning
 * a database and a foreign API can.
 */
public interface IdentityOutbox {

    /**
     * Record, in the caller's transaction, that this identity must be pushed to the provider. Must
     * be called from within the same transaction that writes the user — that co-commit is the whole
     * point; called on its own it is just an unreliable queue.
     */
    void append(String aggregateId, String eventType, UserIdentity identity);

    /**
     * The next changes due for delivery, oldest first, up to {@code limit}. "Due" excludes what is
     * already delivered, what has been abandoned after too many failures, and what is waiting out a
     * backoff — so a caller can loop on this without re-seeing a message it just rescheduled.
     */
    List<PendingIdentityChange> pending(int limit);

    /** Mark a change delivered. It will not be returned by {@link #pending} again. */
    void markDelivered(String id);

    /**
     * Record a failed delivery: count the attempt, keep the reason, and push the next try out by a
     * growing backoff — or give up and abandon the message once it has failed too many times, so a
     * permanently poisonous change stops flooding the log and blocking the batch behind it.
     */
    void reschedule(String id, String error);

    /**
     * Delete delivered messages older than {@code retention}. The outbox keeps them for a while
     * after delivery — an audit of what was propagated and when — but not forever; this is what
     * bounds the table.
     *
     * @return how many rows were removed
     */
    int purgeDeliveredBefore(Duration retention);
}
