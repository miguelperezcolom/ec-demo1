package io.mateu.ecdemo1.users.domain.aggregates.user.events;

import io.mateu.workflow.ddd.DomainEvent;

/**
 * Raised when a user is first created. What a subscriber needs to make the same person exist in an
 * identity provider — and nothing more.
 *
 * <p>Identity only, on purpose. This service is the source of truth for <em>authorization</em> —
 * roles and scopes, which the gateway reads from it to enrich a token — so those are deliberately
 * absent here. Keycloak's copy of the user is for <em>authentication</em>: it needs to know the
 * person exists and whether they may sign in, not what they may then do. Carrying roles in this
 * event would invite a second place where they are decided, which is exactly what the gRPC
 * enrichment exists to avoid.
 *
 * <p>{@code enabled} is the projection of {@code Status}: active means yes, disabled or archived
 * means no. It travels as a boolean because that is the one thing Keycloak has a field for; the
 * three-way distinction stays on this side, where it means something.
 */
public record UserCreated(String userId, String name, String email, boolean enabled)
        implements DomainEvent {
}
