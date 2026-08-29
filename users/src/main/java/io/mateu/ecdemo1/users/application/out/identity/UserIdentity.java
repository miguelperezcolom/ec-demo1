package io.mateu.ecdemo1.users.application.out.identity;

/**
 * The identity of a user as an external provider needs it: enough to make the same person exist and
 * be able (or not) to sign in, and no more.
 *
 * <p>{@code username} is this service's own {@code UserId}. The identity provider needs a stable,
 * unique handle to key its copy on, and the id is exactly that — it never changes and never
 * collides, which an email or a display name cannot promise. It is also what the seed does: the
 * realm's {@code demo} user has username {@code demo}, its id here.
 *
 * <p>{@code firstName} carries the whole display name. This service keeps a person's name as one
 * field, and splitting it on a space to fill Keycloak's two would invent a surname that was never
 * given; the honest mapping is to put what we have where a name goes and leave the other blank.
 */
public record UserIdentity(String userId, String username, String email, String firstName,
                           boolean enabled) {
}
