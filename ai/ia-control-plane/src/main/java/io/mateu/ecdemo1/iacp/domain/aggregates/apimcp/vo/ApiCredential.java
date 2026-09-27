package io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo;

/**
 * What an API asks for before it answers, as the domain is allowed to hold it: encrypted, and
 * nothing else.
 *
 * <p>Deliberately the same shape as an LLM's Credential, and for the same reasons: the plaintext
 * never reaches this type, a use case encrypts what the operator typed before constructing one, and
 * {@link #toString()} stays overridden so that Lombok's generated toString on the aggregate cannot
 * print it into a listing, a log line or a stack trace.
 *
 * <p>It is a separate type rather than a reuse of the LLM's because the two are not the same
 * secret and must not become interchangeable by accident — a method that takes "a Credential"
 * would happily be handed the wrong one.
 */
public record ApiCredential(String cipherText) {

    /** An API catalogued before anyone has been given its key. */
    public static ApiCredential none() { return new ApiCredential(null); }

    public boolean isSet() { return cipherText != null && !cipherText.isBlank(); }

    @Override
    public String toString() { return isSet() ? "ApiCredential[set]" : "ApiCredential[unset]"; }
}
