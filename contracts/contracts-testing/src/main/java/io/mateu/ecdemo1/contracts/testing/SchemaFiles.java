package io.mateu.ecdemo1.contracts.testing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;

/**
 * Keeps a topic's schema file what its records say. Generated, its examples written in and checked
 * against it, then compared with the committed file: with {@code -Dcontracts.write=true} (or
 * {@code CONTRACTS_WRITE=true}) it is written; otherwise a difference fails the build, saying
 * whether it is additive (write it) or breaking (a new version).
 */
public final class SchemaFiles {

    private SchemaFiles() {
    }

    public static boolean writing() {
        return Boolean.getBoolean("contracts.write") || "true".equalsIgnoreCase(System.getenv("CONTRACTS_WRITE"));
    }

    /** The schema the spec produces, examples included and checked. */
    public static ObjectNode render(TopicSpec spec) {
        var schema = SchemaGenerator.generate(spec);
        var examples = SchemaGenerator.examples(spec);
        schema.set("examples", examples);
        var contract = new ContractSchema(schema);
        var problems = new ArrayList<String>();
        examples.forEach(example -> contract.problems(example).forEach(p -> problems.add(p + " in " + example)));
        if (schema.has("oneOf")) {
            var discriminator = schema.get("x-discriminator").asText();
            SchemaGenerator.variants(spec).keySet().forEach(variant -> {
                var covered = false;
                for (var example : examples) {
                    covered |= variant.equals(example.path(discriminator).asText());
                }
                if (!covered) {
                    problems.add("no example of " + variant);
                }
            });
        }
        if (examples.isEmpty()) {
            problems.add("no example");
        }
        if (!problems.isEmpty()) {
            throw new AssertionError("The examples of %s v%d do not fit its schema:\n  %s".formatted(
                    spec.topic, spec.version, String.join("\n  ", problems)));
        }
        return schema;
    }

    /** Writes the topic's schema, or fails when the committed one is not what the records say. */
    public static void publish(TopicSpec spec) {
        var schema = render(spec);
        var file = Contracts.file(spec.topic, spec.version);
        var text = text(schema);
        try {
            var committed = Files.exists(file) ? Files.readString(file) : null;
            if (text.equals(committed)) {
                return;
            }
            if (writing()) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, text);
                return;
            }
            if (committed == null) {
                throw new AssertionError("%s v%d has no schema yet: run the build with -Dcontracts.write=true and commit %s"
                        .formatted(spec.topic, spec.version, file));
            }
            var breaking = Compatibility.breakingChanges(ContractJson.mapper().readTree(committed), schema);
            throw new AssertionError(breaking.isEmpty()
                    ? "%s v%d changed, compatibly: run the build with -Dcontracts.write=true and commit %s"
                    .formatted(spec.topic, spec.version, file)
                    : ("%s v%d changed in a way its consumers may not read:\n  %s\nPublish it as a new version "
                    + "(TopicSpec.version) and keep v%d while anything reads it.")
                    .formatted(spec.topic, spec.version, String.join("\n  ", breaking), spec.version));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String text(JsonNode schema) {
        try {
            return ContractJson.mapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(schema) + "\n";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
