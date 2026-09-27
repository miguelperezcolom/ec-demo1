package io.mateu.ecdemo1.mapping.queries;

/** A search box's text as a LIKE pattern: contained anywhere, case-insensitive, its own % and _ taken literally. */
final class Like {

    static final char ESCAPE = '\\';

    private Like() {
    }

    /** "%text%", lower-cased; "%" for no text. Compare it against a lower-cased column, with {@code escape '\'}. */
    static String contains(String text) {
        if (text == null || text.isBlank()) {
            return "%";
        }
        var escaped = text.trim().toLowerCase()
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
