package io.mateu.ecdemo1.users.application.usecases.user.identity;

import io.mateu.ecdemo1.users.application.out.identity.UserIdentity;
import io.mateu.core.infra.JsonSerializer;
import io.mateu.ecdemo1.messaging.Outbox;
import io.mateu.ecdemo1.users.domain.aggregates.user.User;
import io.mateu.ecdemo1.users.domain.aggregates.user.events.UserCreated;
import io.mateu.ecdemo1.users.domain.aggregates.user.events.UserDeleted;
import io.mateu.ecdemo1.users.domain.aggregates.user.events.UserUpdated;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Drains a saved user's domain events into the identity outbox, translating each into the shape the
 * provider needs.
 *
 * <p>This is the bridge between two vocabularies. The aggregate speaks of what happened —
 * {@link UserCreated}, {@link UserUpdated} — while the outbox and the provider speak of a
 * {@link UserIdentity} to converge on. Keeping the translation here, in one small class, is what
 * lets the aggregate stay ignorant of Keycloak and the outbox stay ignorant of the domain: neither
 * has to name the other, because this does.
 *
 * <p>It must run inside the use case's transaction, right after the save. The events come off the
 * aggregate with {@code popEvents}, which empties them — so this is called exactly once per save,
 * and calling it drains the events into the same transaction that wrote the user. That co-commit is
 * the outbox's entire guarantee; move this outside the transaction and it is gone.
 *
 * <p>The outbox is the shared one ({@link Outbox}): each change goes to the {@link #DESTINATION}
 * destination, keyed by the user — so one user's changes reach the provider in the order they were
 * made — and its relay hands it to the provider (infra/out/keycloak/IdentityTransport), with a backoff
 * on failure and abandoning it after too many (application.yaml, {@code messaging.outbox.*}).
 *
 * <p>Events it does not recognise are ignored rather than rejected. Today those are the only two,
 * but an aggregate that later raises an event about groups or roles should not break identity
 * propagation by existing — this cares about identity and lets the rest pass.
 */
@Service
@RequiredArgsConstructor
public class IdentityOutboxAppender {

    /** Event-type tags the relay reads to tell a removal from an upsert. Written here, read there. */
    public static final String CREATED = "UserCreated";
    public static final String UPDATED = "UserUpdated";
    public static final String DELETED = "UserDeleted";
    /** Where the identity changes go in the outbox: the identity provider. */
    public static final String DESTINATION = "identity";

    private final Outbox outbox;

    /** In the caller's transaction: the change commits with the user, or not at all. */
    void append(String userId, String eventType, UserIdentity identity) {
        outbox.append(DESTINATION, userId, eventType, JsonSerializer.toJson(identity), null);
    }

    public void drain(User user) {
        for (var event : user.popEvents()) {
            if (event instanceof UserCreated e) {
                append(e.userId(), CREATED,
                        new UserIdentity(e.userId(), e.userId(), e.email(), e.name(), e.enabled()));
            } else if (event instanceof UserUpdated e) {
                append(e.userId(), UPDATED,
                        new UserIdentity(e.userId(), e.userId(), e.email(), e.name(), e.enabled()));
            } else if (event instanceof UserDeleted e) {
                // A deletion needs only the username to key on. The other fields are left blank —
                // there is no identity to describe once it is gone.
                append(e.userId(), DELETED,
                        new UserIdentity(e.userId(), e.userId(), null, null, false));
            }
        }
    }
}
