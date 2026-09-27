package io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo;

/** A human-readable name. Blank is not a name, and the catalogues are read by people. */
public record Name(String value) {
    public Name {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A name is required");
        }
    }
    @Override public String toString() { return value; }
}
