package io.mateu.ecdemo1.users.application.usecases.user.directory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.messaging.Outbox;
import io.mateu.ecdemo1.users.domain.aggregates.user.User;
import io.mateu.ecdemo1.users.domain.aggregates.user.events.UserCreated;
import io.mateu.ecdemo1.users.domain.aggregates.user.events.UserDeleted;
import io.mateu.ecdemo1.users.domain.aggregates.user.events.UserUpdated;
import io.mateu.workflow.ddd.DomainEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Appends a saved user's domain events to the outbox for the {@value #TOPIC} topic, as
 * {@link EmployeeDirectoryMessage}s — the feed the content service builds its employee directory
 * from. Same co-commit as the identity outbox: called inside the use case's transaction, so "the user
 * changed" and "tell the directory" commit together or not at all.
 *
 * <p>The outbox key is the user id, prefixed with the topic ({@link #outboxKey}). The relay keeps the
 * messages of one key in order and holds them all behind a failed one; with the bare user id the
 * directory would share that queue with the identity changes, and a Keycloak outage would stop the
 * directory too. The prefix gives the directory a queue of its own per user; the transport strips it
 * again, so the Kafka record's key is the bare user id the contract promises.
 */
@Service
public class EmployeeDirectoryOutboxAppender {

    public static final String TOPIC = "employee-directory";
    /** What prefixes the user id in the outbox key, and what the transport strips. */
    public static final String KEY_PREFIX = TOPIC + "/";

    static final ObjectMapper JSON = new ObjectMapper();

    private final Outbox outbox;
    private final Clock clock;

    @Autowired
    public EmployeeDirectoryOutboxAppender(Outbox outbox) {
        this(outbox, Clock.systemUTC());
    }

    EmployeeDirectoryOutboxAppender(Outbox outbox, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
    }

    public static String outboxKey(String userId) {
        return KEY_PREFIX + userId;
    }

    /** In the caller's transaction. Events that are not about the employee are let pass. */
    public void append(List<DomainEvent> events) {
        for (var event : events) {
            toMessage(event, UUID.randomUUID().toString(), clock.instant().toString()).ifPresent(this::append);
        }
    }

    /** The user as they are now, as an upsert — what a snapshot sends for each user. */
    public void appendCurrent(User user) {
        append(EmployeeDirectoryMessage.upserted(UUID.randomUUID().toString(), user.getId().id(),
                user.getName().name(), user.getEmail().email(), user.isEnabled(), user.getAttributes(),
                clock.instant().toString()));
    }

    void append(EmployeeDirectoryMessage message) {
        outbox.append(TOPIC, outboxKey(message.userId()), message.type(), toJson(message), null);
    }

    /** The message a domain event becomes on the topic, if it becomes one. */
    static Optional<EmployeeDirectoryMessage> toMessage(DomainEvent event, String eventId, String occurredAt) {
        if (event instanceof UserCreated e) {
            return Optional.of(EmployeeDirectoryMessage.upserted(eventId, e.userId(), e.name(), e.email(),
                    e.enabled(), e.attributes(), occurredAt));
        }
        if (event instanceof UserUpdated e) {
            return Optional.of(EmployeeDirectoryMessage.upserted(eventId, e.userId(), e.name(), e.email(),
                    e.enabled(), e.attributes(), occurredAt));
        }
        if (event instanceof UserDeleted e) {
            return Optional.of(EmployeeDirectoryMessage.removed(eventId, e.userId(), occurredAt));
        }
        return Optional.empty();
    }

    /** Compact JSON, UTF-8 on the wire (the Kafka transport encodes it). */
    static String toJson(EmployeeDirectoryMessage message) {
        try {
            return JSON.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
