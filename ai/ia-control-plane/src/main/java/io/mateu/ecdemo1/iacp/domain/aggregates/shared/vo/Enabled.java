package io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo;

/**
 * Whether a catalogue entry is in play.
 *
 * <p>Every one of the four catalogues has it, and it means the same thing in all four: a disabled
 * entry stays in the catalogue, keeps its history and its id, and is left out of the configuration
 * the agent is served. It is the difference between "we are not using this today" and "delete it",
 * and only the first of those is reversible.
 */
public record Enabled(boolean value) {
    public static Enabled yes() { return new Enabled(true); }
    public static Enabled no() { return new Enabled(false); }
    @Override public String toString() { return value ? "enabled" : "disabled"; }
}
