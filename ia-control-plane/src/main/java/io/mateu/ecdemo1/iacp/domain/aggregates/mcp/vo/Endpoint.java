package io.mateu.ecdemo1.iacp.domain.aggregates.mcp.vo;

import java.net.URI;

/**
 * Where a server lives.
 *
 * <p>Validated as an absolute http(s) URI at construction rather than when something tries to
 * connect, because the alternative is a catalogue entry that looks fine in a listing and fails
 * only inside an agent's prompt, minutes later, as a tool that is quietly missing.
 */
public record Endpoint(String value) {
    public Endpoint {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A URL is required");
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Not a valid URL: " + value);
        }
        if (!uri.isAbsolute() || uri.getHost() == null) {
            throw new IllegalArgumentException("A URL must be absolute and name a host: " + value);
        }
        var scheme = uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Only http and https are supported: " + value);
        }
    }
    @Override public String toString() { return value; }
}
