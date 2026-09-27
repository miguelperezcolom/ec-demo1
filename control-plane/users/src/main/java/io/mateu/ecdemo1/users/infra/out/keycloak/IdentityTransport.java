package io.mateu.ecdemo1.users.infra.out.keycloak;

import io.mateu.core.infra.JsonSerializer;
import io.mateu.ecdemo1.messaging.OutboxMessage;
import io.mateu.ecdemo1.messaging.OutboxTransport;
import io.mateu.ecdemo1.users.application.out.identity.IdentityProviderPort;
import io.mateu.ecdemo1.users.application.out.identity.UserIdentity;
import io.mateu.ecdemo1.users.application.usecases.user.identity.IdentityOutboxAppender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Where the shared outbox's relay delivers this service's messages: the identity provider. A change
 * carries the identity as it was when appended (frozen as JSON), and the provider's upsert and delete
 * are idempotent — the relay delivers at least once. One that throws is retried by the relay after a
 * backoff, the user's later changes waiting behind it, and abandoned after
 * {@code messaging.outbox.max-attempts}: the row stays, with its last error, for someone to find.
 */
@Component
@RequiredArgsConstructor
public class IdentityTransport implements OutboxTransport {

    private final IdentityProviderPort identityProvider;

    @Override
    public void send(OutboxMessage message, Map<String, byte[]> headers) {
        if (!IdentityOutboxAppender.DESTINATION.equals(message.destination())) {
            throw new IllegalStateException("No transport for " + message.destination());
        }
        var identity = JsonSerializer.pojoFromJson(message.payload(), UserIdentity.class);
        if (IdentityOutboxAppender.DELETED.equals(message.type())) {
            identityProvider.deleteUser(identity);
        } else {
            identityProvider.upsertUser(identity);
        }
    }
}
