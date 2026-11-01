package io.mateu.ecdemo1.gateway.infra.enrichment;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * What the users service says a user holds: role ids and permission scopes, each as one
 * space-separated string — the shape AuthService answers with and the shape the headers carry, so
 * nothing downstream has to learn a second encoding.
 *
 * <p>Normalised on the way in (runs of whitespace collapsed, ends trimmed) because the service
 * joins with a single space but nothing guarantees an id or a scope never carries one of its own
 * at an edge, and a header value with a leading space reads as an empty first entry to a naive
 * {@code split(" ")}.
 */
public record AuthInfo(String roles, String scopes) {

    /** A user the users service does not know: it grants nothing, which is an answer, not a failure. */
    public static final AuthInfo NONE = new AuthInfo("", "");

    public AuthInfo {
        roles = normalise(roles);
        scopes = normalise(scopes);
    }

    private static String normalise(String value) {
        if (value == null) {
            return "";
        }
        return Arrays.stream(value.trim().split("\\s+"))
                .filter(token -> !token.isEmpty())
                .collect(Collectors.joining(" "));
    }
}
