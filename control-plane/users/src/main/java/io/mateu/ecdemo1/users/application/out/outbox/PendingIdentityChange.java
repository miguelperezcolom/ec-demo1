package io.mateu.ecdemo1.users.application.out.outbox;

import io.mateu.ecdemo1.users.application.out.identity.UserIdentity;

/**
 * One undelivered identity change, as the relay sees it: the provider payload it must deliver
 * ({@code identity}), the handle it needs to mark the outcome ({@code id}), and what it is for the
 * log ({@code eventType}, {@code attempts}).
 *
 * <p>The stored form — a serialized row with a JSON column — does not appear here. That is the
 * outbox adapter's business; the relay works in domain terms and never sees the wire.
 */
public record PendingIdentityChange(String id, String eventType, UserIdentity identity,
                                    int attempts) {
}
