package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.regex.Pattern;

/** Small helpers for the adapters' SQL. */
final class Sql {

    static final Pattern SAFE_TOKEN = Pattern.compile("[A-Za-z0-9_-]+");

    private Sql() {
    }

    static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    /**
     * A text[] literal for hashes, cast with {@code ?::text[]}. Hashes and the TODOS token are letters and
     * digits only: anything else is refused rather than quoted.
     */
    static String array(Collection<String> values) {
        for (var v : values) {
            if (!SAFE_TOKEN.matcher(v).matches()) {
                throw new IllegalArgumentException("Not a hash: " + v);
            }
        }
        return "{" + String.join(",", values) + "}";
    }

    static String like(String text) {
        return "%" + (text == null ? "" : text.trim().toLowerCase().replace("%", "").replace("_", "")) + "%";
    }
}
