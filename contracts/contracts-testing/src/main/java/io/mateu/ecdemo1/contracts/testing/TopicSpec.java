package io.mateu.ecdemo1.contracts.testing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One Kafka topic's contract, as its owner declares it: what travels on it (a record, or a sealed
 * interface whose {@code @JsonSubTypes} are the variants), who owns it, what it is keyed by, who
 * produces and consumes it, and an example of every variant. {@link SchemaFiles#publish} turns it
 * into {@code contracts/schemas/<topic>/v<version>.schema.json}.
 *
 * <p>The owner is whoever defines the language: the producer for events, the receiving service for
 * commands. A breaking change (a field removed or retyped, a variant or an enum value gone, a new
 * required field) is a new {@code version}; the old file stays for as long as anything reads it.
 */
public final class TopicSpec {

    final String topic;
    int version = 1;
    String owner;
    String key;
    String description;
    final List<String> producers = new ArrayList<>();
    final List<String> consumers = new ArrayList<>();
    Class<?> root;
    String discriminator;
    final Map<String, Class<?>> variants = new LinkedHashMap<>();
    boolean closed = true;
    final List<Object> examples = new ArrayList<>();

    private TopicSpec(String topic) {
        this.topic = topic;
    }

    public static TopicSpec topic(String topic) {
        return new TopicSpec(topic);
    }

    public TopicSpec version(int version) {
        this.version = version;
        return this;
    }

    public TopicSpec ownedBy(String owner) {
        this.owner = owner;
        return this;
    }

    /** What the record's key is, in words ({@code hotelCode/locator}, {@code customerId}). */
    public TopicSpec keyedBy(String key) {
        this.key = key;
        return this;
    }

    public TopicSpec describedAs(String description) {
        this.description = description;
        return this;
    }

    public TopicSpec producedBy(String... services) {
        producers.addAll(List.of(services));
        return this;
    }

    public TopicSpec consumedBy(String... services) {
        consumers.addAll(List.of(services));
        return this;
    }

    /**
     * The messages: a record, or a type annotated with {@code @JsonTypeInfo(property = ...)} and
     * {@code @JsonSubTypes}, whose subtypes are then the variants.
     */
    public TopicSpec messages(Class<?> root) {
        this.root = root;
        return this;
    }

    /**
     * Variants the root does not declare itself — types registered on a mapper by name
     * ({@code registerSubtypes(new NamedType(...))}), told apart by {@code discriminator}.
     */
    public TopicSpec variant(String discriminator, String name, Class<?> type) {
        this.discriminator = discriminator;
        variants.put(name, type);
        return this;
    }

    /**
     * Open: a message may carry properties the schema does not name (a producer whose record is
     * wider than the owner's). Closed, the default: a property the schema does not know is a
     * producer the schema no longer describes.
     */
    public TopicSpec open() {
        this.closed = false;
        return this;
    }

    public TopicSpec example(Object... examples) {
        this.examples.addAll(List.of(examples));
        return this;
    }

    public String topic() {
        return topic;
    }

    public int version() {
        return version;
    }
}
