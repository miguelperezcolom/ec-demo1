package io.mateu.ecdemo1.contracts.testing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * A topic's schema: what a producer checks its messages against, and the examples a consumer
 * checks it can read.
 */
public final class ContractSchema {

    final JsonNode schema;
    final JsonSchema validator;

    ContractSchema(JsonNode schema) {
        this.schema = schema;
        var copy = ((ObjectNode) schema.deepCopy());
        copy.remove("$id");
        this.validator = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(copy, SchemaValidatorsConfig.builder().locale(Locale.ENGLISH).build());
    }

    static ContractSchema read(Path file) {
        try {
            return new ContractSchema(ContractJson.mapper().readTree(file.toFile()));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    public String topic() {
        return schema.path("x-topic").asText();
    }

    public int version() {
        return schema.path("x-version").asInt();
    }

    public JsonNode schema() {
        return schema;
    }

    /** What is wrong with a message, by the schema; empty when nothing is. */
    public List<String> problems(String json) {
        try {
            return problems(ContractJson.mapper().readTree(json));
        } catch (IOException e) {
            return List.of("not JSON: " + e.getMessage());
        }
    }

    public List<String> problems(JsonNode message) {
        var messages = validator.validate(message);
        return messages.stream().map(ValidationMessage::getMessage).sorted().collect(Collectors.toList());
    }

    /** Fails, naming every problem, when the message is not what the topic's schema says. */
    public void assertValid(String json) {
        var problems = problems(json);
        if (!problems.isEmpty()) {
            throw new AssertionError("A message on %s is not what v%d of its schema says:\n  %s\n%s".formatted(
                    topic(), version(), String.join("\n  ", problems), json));
        }
    }

    /** The schema's examples, as JSON text — what a consumer must be able to read. */
    public List<String> examples() {
        var result = new ArrayList<String>();
        schema.path("examples").forEach(example -> result.add(example.toString()));
        if (result.isEmpty()) {
            throw new IllegalStateException("The schema of " + topic() + " has no examples");
        }
        return result;
    }

    /** The examples of one variant (the discriminator's value), for a polymorphic topic. */
    public List<String> examples(String variant) {
        var discriminator = schema.path("x-discriminator").asText();
        var result = new ArrayList<String>();
        schema.path("examples").forEach(example -> {
            if (variant.equals(example.path(discriminator).asText())) {
                result.add(example.toString());
            }
        });
        if (result.isEmpty()) {
            throw new IllegalStateException("The schema of " + topic() + " has no example of " + variant);
        }
        return result;
    }
}
