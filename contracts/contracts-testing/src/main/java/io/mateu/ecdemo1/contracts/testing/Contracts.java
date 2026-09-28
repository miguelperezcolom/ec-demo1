package io.mateu.ecdemo1.contracts.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * Where the schemas are — {@code contracts/schemas} of this repository, found from the directory a
 * test runs in — and a topic's schema by name: its latest version, or one version.
 */
public final class Contracts {

    static final Pattern VERSION = Pattern.compile("v(\\d+)\\.schema\\.json");

    private Contracts() {
    }

    public static Path schemas() {
        var configured = System.getProperty("contracts.schemas");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        for (var dir = Path.of(System.getProperty("user.dir")).toAbsolutePath(); dir != null; dir = dir.getParent()) {
            var candidate = dir.resolve("contracts").resolve("schemas");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No contracts/schemas above " + System.getProperty("user.dir")
                + " (or set -Dcontracts.schemas)");
    }

    public static Path file(String topic, int version) {
        return schemas().resolve(topic).resolve("v%d.schema.json".formatted(version));
    }

    /** The topic's latest schema. */
    public static ContractSchema topic(String topic) {
        try (var files = Files.list(schemas().resolve(topic))) {
            var latest = files.map(f -> VERSION.matcher(f.getFileName().toString()))
                    .filter(java.util.regex.Matcher::matches)
                    .map(m -> Integer.parseInt(m.group(1)))
                    .max(Comparator.naturalOrder())
                    .orElseThrow(() -> new IllegalStateException("No schema for topic " + topic));
            return topic(topic, latest);
        } catch (IOException e) {
            throw new UncheckedIOException("No schema for topic " + topic, e);
        }
    }

    public static ContractSchema topic(String topic, int version) {
        return ContractSchema.read(file(topic, version));
    }
}
