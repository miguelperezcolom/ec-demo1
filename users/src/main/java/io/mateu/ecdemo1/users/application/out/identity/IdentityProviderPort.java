package io.mateu.ecdemo1.users.application.out.identity;

/**
 * The way out to whatever holds the authentication copy of a user — Keycloak here, but the port
 * names none of it.
 *
 * <p>One method, and it is an upsert rather than a create/update pair, because the caller is a
 * relay draining an at-least-once outbox: the same change may arrive more than once, and a create
 * that fails on the second delivery because the user already exists would be a permanent error for
 * a message that was in fact already handled. {@code upsertUser} is defined to be safe to apply any
 * number of times and to converge on the same result, which is the only contract that survives
 * redelivery.
 *
 * <p>It returns nothing and throws on failure. The relay reads a thrown exception as "not
 * delivered, try again later" and a clean return as "done"; there is no third outcome to report.
 */
public interface IdentityProviderPort {

    /**
     * Make the provider's copy of this user match {@code user}: create it if absent, update it if
     * present. Idempotent.
     *
     * @throws RuntimeException if the provider could not be reached or refused the change. The
     *         caller will retry.
     */
    void upsertUser(UserIdentity user);

    /**
     * Remove the provider's copy of this user, keyed on {@code user.username()}. Idempotent: a user
     * that is already gone is a success, not an error, because an at-least-once relay may deliver
     * the same deletion twice and the second must not fail.
     *
     * @throws RuntimeException if the provider could not be reached or refused the deletion. The
     *         caller will retry.
     */
    void deleteUser(UserIdentity user);
}
