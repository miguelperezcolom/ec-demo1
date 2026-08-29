package io.mateu.ecdemo1.users.domain.aggregates.user.events;

import io.mateu.workflow.ddd.DomainEvent;

/**
 * Raised when a user is removed. Carries only the id, because that is all a subscriber needs to
 * find its own copy and remove it too — there is no identity left to describe.
 *
 * <p>Propagating this one matters more than it looks. A user deleted here but left in the identity
 * provider can still authenticate; the account outlives the record that was supposed to govern it.
 * So deletion is not a courtesy sync, it is the half of the lifecycle that closes the door — which
 * is exactly why it goes through the same at-least-once outbox as the rest, rather than a
 * best-effort call that a brief Keycloak outage could drop on the floor.
 */
public record UserDeleted(String userId) implements DomainEvent {
}
