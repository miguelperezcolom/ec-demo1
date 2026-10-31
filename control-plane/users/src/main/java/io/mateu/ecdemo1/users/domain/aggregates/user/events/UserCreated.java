package io.mateu.ecdemo1.users.domain.aggregates.user.events;

import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeAttributes;
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
 *
 * <p>{@code attributes} are the employee attributes (company, hotel, department…), and they are
 * for a different subscriber: the employee directory the content service keeps, which decides who
 * sees what from them. They do not go to the identity provider — the identity translation drops
 * them — because an audience is decided from the directory, not from the token.
 */
public record UserCreated(String userId, String name, String email, boolean enabled,
                          EmployeeAttributes attributes)
        implements DomainEvent {
}
