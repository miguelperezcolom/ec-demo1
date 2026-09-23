package io.mateu.ecdemo1.mdm.resolution;

import java.text.Normalizer.Form;
import java.util.Locale;

/**
 * Canonical forms for comparing (HLA CRM-MDM, «Normalizar»): the same email, document or name
 * written two ways must compare equal. Only for matching — what is stored is what was given.
 */
public final class Normalizer {

    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Type and number, the number without the dots, dashes and spaces people type into it. */
    public static String document(String type, String number) {
        if (number == null || number.isBlank()) {
            return null;
        }
        var clean = number.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return (type == null || type.isBlank() ? "DOC" : type.trim().toUpperCase(Locale.ROOT)) + ":" + clean;
    }

    /** Letters only, no accents, lower case: "García" and "garcia" are the same surname. */
    public static String name(String first, String last) {
        var joined = ((first == null ? "" : first) + " " + (last == null ? "" : last));
        var plain = java.text.Normalizer.normalize(joined, Form.NFD).replaceAll("\\p{M}", "");
        var key = plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return key.isEmpty() ? null : key;
    }

    private Normalizer() {
    }
}
