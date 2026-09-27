package io.mateu.ecdemo1.iacp.domain.aggregates.llm.vo;

/**
 * The provider's own model identifier, verbatim — {@code claude-opus-5}, {@code gpt-5}.
 *
 * <p>Deliberately not an enum. A model id is the provider's namespace, not ours: new ones appear
 * between releases of this service, and an enum would mean a rebuild to name a model that already
 * exists. The catalogue's job is to record which one was chosen, not to have an opinion about
 * which ones exist.
 */
public record ModelName(String value) {
    public ModelName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A model name is required");
        }
    }
    @Override public String toString() { return value; }
}
