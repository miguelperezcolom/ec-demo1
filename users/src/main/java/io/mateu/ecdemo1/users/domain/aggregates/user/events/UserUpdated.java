package io.mateu.ecdemo1.users.domain.aggregates.user.events;

import io.mateu.workflow.ddd.DomainEvent;

/**
 * Raised when a user's identity changes. Same shape as {@link UserCreated}, and that is not an
 * accident: the subscriber does not act differently on the two. Delivery is an idempotent upsert —
 * create the identity provider's user if absent, update it if present — so what matters downstream
 * is the current state of the person, which both events carry in full.
 *
 * <p>The two types are kept apart anyway, because "a user appeared" and "a user changed" read
 * differently in a log and count differently in a metric, and collapsing them would throw that
 * away for no saving.
 */
public record UserUpdated(String userId, String name, String email, boolean enabled)
        implements DomainEvent {
}
