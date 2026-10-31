package io.mateu.ecdemo1.contenthub.domain.employee;

import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * An employee's attributes, as the visibility function reads them, and the language they read in.
 *
 * <p>{@link #hash()} names everyone who sees exactly the same: it is computed from the visibility
 * attributes alone, normalised (trimmed, upper case, blank = absent, in attribute order), so two
 * employees with the same attributes share caches, newsletter groups and the projection's entries.
 *
 * @param values   the visibility attributes that are set; absent ones are not in the map
 * @param language the language the employee reads in (ISO code, lower case); not part of the hash
 */
public record EmployeeAttributes(Map<Attribute, String> values, String language) {

    public static final String DEFAULT_LANGUAGE = "es";

    /** The token in the projection that stands for "every employee" (an empty audience). */
    public static final String EVERYONE_TOKEN = "TODOS";

    public static final EmployeeAttributes NONE = new EmployeeAttributes(Map.of(), DEFAULT_LANGUAGE);

    public EmployeeAttributes {
        var normalised = new EnumMap<Attribute, String>(Attribute.class);
        if (values != null) {
            values.forEach((k, v) -> {
                if (k != null && v != null && !v.isBlank()) {
                    normalised.put(k, normalise(v));
                }
            });
        }
        values = Map.copyOf(normalised);
        language = language == null || language.isBlank() ? DEFAULT_LANGUAGE : language.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalise(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    public Optional<String> get(Attribute attribute) {
        return Optional.ofNullable(values.get(attribute));
    }

    /** The canonical text the hash is computed from: "COMPANY=X;HOTEL=Y" in attribute order. */
    public String canonical() {
        return values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey().name() + "=" + e.getValue())
                .collect(Collectors.joining(";"));
    }

    public String hash() {
        return hashOf(canonical());
    }

    static String hashOf(String canonical) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return "h" + HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public EmployeeAttributes withLanguage(String language) {
        return new EmployeeAttributes(values, language);
    }
}
