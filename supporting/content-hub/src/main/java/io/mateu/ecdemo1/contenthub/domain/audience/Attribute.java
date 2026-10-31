package io.mateu.ecdemo1.contenthub.domain.audience;

import java.util.Locale;
import java.util.Optional;

/**
 * The closed list of employee attributes an audience may be written over. The language is not one of
 * them: it chooses a translation, never what is visible — and so it stays out of the profile hash.
 *
 * <p>{@link #key()} is the name the users service uses in the employee-directory messages.
 */
public enum Attribute {
    COMPANY("company", "Empresa"),
    COUNTRY("country", "País"),
    REGION("region", "Región"),
    HOTEL("hotel", "Hotel"),
    DEPARTMENT("department", "Departamento"),
    JOB_TITLE("jobTitle", "Puesto");

    private final String key;
    private final String label;

    Attribute(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public static Optional<Attribute> ofKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        for (var a : values()) {
            if (a.key.equalsIgnoreCase(key) || a.name().equalsIgnoreCase(key.toUpperCase(Locale.ROOT))) {
                return Optional.of(a);
            }
        }
        return Optional.empty();
    }
}
