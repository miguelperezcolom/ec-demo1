package io.mateu.ecdemo1.users.application.usecases.user.directory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.messaging.Outbox;
import io.mateu.ecdemo1.users.application.out.AttributeValueRepository;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.AttributeValue;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.Attribute;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.AttributeValueId;
import io.mateu.ecdemo1.users.domain.aggregates.user.User;
import io.mateu.ecdemo1.users.domain.aggregates.user.events.UserCreated;
import io.mateu.ecdemo1.users.domain.aggregates.user.events.UserDeleted;
import io.mateu.ecdemo1.users.domain.aggregates.user.events.UserUpdated;
import io.mateu.workflow.ddd.DomainEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

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
    /** A catalogue code's label, or null: what the message's {@code labels} are made of. */
    private final Function<AttributeValueId, String> labels;

    @Autowired
    public EmployeeDirectoryOutboxAppender(Outbox outbox, AttributeValueRepository catalogue) {
        this(outbox, Clock.systemUTC(), id -> catalogue.findById(id).map(AttributeValue::getLabel).orElse(null));
    }

    /** Without a catalogue: messages go without labels. */
    public EmployeeDirectoryOutboxAppender(Outbox outbox) {
        this(outbox, Clock.systemUTC(), id -> null);
    }

    EmployeeDirectoryOutboxAppender(Outbox outbox, Clock clock, Function<AttributeValueId, String> labels) {
        this.outbox = outbox;
        this.clock = clock;
        this.labels = labels;
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
                user.getProfile(), clock.instant().toString()));
    }

    void append(EmployeeDirectoryMessage message) {
        message = message.withLabels(labelsOf(message.attributes()));
        outbox.append(TOPIC, outboxKey(message.userId()), message.type(), toJson(message), null);
    }

    /** The message a domain event becomes on the topic, if it becomes one. */
    static Optional<EmployeeDirectoryMessage> toMessage(DomainEvent event, String eventId, String occurredAt) {
        if (event instanceof UserCreated e) {
            return Optional.of(EmployeeDirectoryMessage.upserted(eventId, e.userId(), e.name(), e.email(),
                    e.enabled(), e.attributes(), e.profile(), occurredAt));
        }
        if (event instanceof UserUpdated e) {
            return Optional.of(EmployeeDirectoryMessage.upserted(eventId, e.userId(), e.name(), e.email(),
                    e.enabled(), e.attributes(), e.profile(), occurredAt));
        }
        if (event instanceof UserDeleted e) {
            return Optional.of(EmployeeDirectoryMessage.removed(eventId, e.userId(), occurredAt));
        }
        return Optional.empty();
    }

    /** The labels of the codes an employee has, keyed like the attributes ({@code hotel}, {@code jobTitle}). */
    Map<String, String> labelsOf(EmployeeDirectoryMessage.Attributes a) {
        var out = new LinkedHashMap<String, String>();
        if (a == null) {
            return out;
        }
        put(out, "company", Attribute.COMPANY, a.company());
        put(out, "country", Attribute.COUNTRY, a.country());
        put(out, "region", Attribute.REGION, a.region());
        put(out, "hotel", Attribute.HOTEL, a.hotel());
        put(out, "department", Attribute.DEPARTMENT, a.department());
        put(out, "jobTitle", Attribute.JOB_TITLE, a.jobTitle());
        return out;
    }

    private void put(Map<String, String> out, String key, Attribute attribute, String code) {
        if (code == null) {
            return;
        }
        var label = labels.apply(AttributeValueId.of(attribute, code));
        if (label != null && !label.isBlank()) {
            out.put(key, label);
        }
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
