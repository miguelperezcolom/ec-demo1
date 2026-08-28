package io.mateu.ecdemo1.iacp.domain.aggregates.llm.vo;

/**
 * An LLM's API key, as the domain is allowed to hold it: encrypted, and nothing else.
 *
 * <p>The plaintext never reaches this type. A use case encrypts what the operator typed before
 * constructing one, so an aggregate in memory, an entity in the database and anything that
 * serialises either of them all carry ciphertext. That is what makes it safe for the UI to load
 * an LLM for editing — there is no plaintext on the object to leak into a listing, a log line or
 * a stack trace.
 *
 * <p>{@link #toString()} is overridden for the same reason and must stay overridden: Lombok's
 * generated toString on the aggregate would otherwise print whatever this holds.
 */
public record Credential(String cipherText) {

    /** An LLM that has no credential yet — legitimate while one is being catalogued. */
    public static Credential none() { return new Credential(null); }

    public boolean isSet() { return cipherText != null && !cipherText.isBlank(); }

    @Override
    public String toString() { return isSet() ? "Credential[set]" : "Credential[unset]"; }
}
