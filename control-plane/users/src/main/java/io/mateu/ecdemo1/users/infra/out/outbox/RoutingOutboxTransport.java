package io.mateu.ecdemo1.users.infra.out.outbox;

import io.mateu.ecdemo1.messaging.OutboxMessage;
import io.mateu.ecdemo1.messaging.OutboxTransport;
import io.mateu.ecdemo1.messaging.transport.KafkaTemplateTransport;
import io.mateu.ecdemo1.users.application.usecases.user.directory.EmployeeDirectoryOutboxAppender;
import io.mateu.ecdemo1.users.application.usecases.user.identity.IdentityOutboxAppender;
import io.mateu.ecdemo1.users.infra.out.keycloak.IdentityTransport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * The one transport the shared outbox's relay uses here ({@code @Primary}: {@link IdentityTransport} is
 * a transport bean too), sending each message by its destination: {@code identity} to Keycloak, as
 * before, and {@code employee-directory} to Kafka — the topic the content service builds its employee
 * directory from. Anything else is a programming error, thrown like {@link IdentityTransport} throws,
 * so it stays in the outbox with the reason rather than vanishing.
 *
 * <p>The directory's outbox key is the user id with a prefix (see
 * {@link EmployeeDirectoryOutboxAppender}), so its messages do not queue behind the user's identity
 * changes; the prefix is stripped here, and the Kafka record goes out keyed by the bare user id.
 */
@Component
@Primary
public class RoutingOutboxTransport implements OutboxTransport {

    final OutboxTransport identity;
    final OutboxTransport directory;

    @Autowired
    public RoutingOutboxTransport(IdentityTransport identity, KafkaOperations<String, byte[]> kafka) {
        this(identity, new KafkaTemplateTransport(kafka, Duration.ofSeconds(15)));
    }

    RoutingOutboxTransport(OutboxTransport identity, OutboxTransport directory) {
        this.identity = identity;
        this.directory = directory;
    }

    @Override
    public void send(OutboxMessage message, Map<String, byte[]> headers) throws Exception {
        switch (message.destination()) {
            case IdentityOutboxAppender.DESTINATION -> identity.send(message, headers);
            case EmployeeDirectoryOutboxAppender.TOPIC -> directory.send(withBareKey(message), headers);
            default -> throw new IllegalStateException("No transport for " + message.destination());
        }
    }

    static OutboxMessage withBareKey(OutboxMessage m) {
        var key = m.key();
        if (key != null && key.startsWith(EmployeeDirectoryOutboxAppender.KEY_PREFIX)) {
            key = key.substring(EmployeeDirectoryOutboxAppender.KEY_PREFIX.length());
        }
        return new OutboxMessage(m.seq(), m.destination(), key, m.type(), m.payload(), m.headers(),
                m.traceparent(), m.tracestate(), m.attempts(), m.createdAt(), m.publishedAt());
    }
}
