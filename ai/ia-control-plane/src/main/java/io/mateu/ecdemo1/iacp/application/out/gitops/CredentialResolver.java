package io.mateu.ecdemo1.iacp.application.out.gitops;

/**
 * Turns the env-var name an LLM entry carries into the actual secret, read from this deployment's
 * own environment. The indirection is the whole point: the repo says {@code ANTHROPIC_API_KEY}, the
 * Secret holds its value, and the two never meet in version control.
 */
public interface CredentialResolver {

    /**
     * @return the value of the named environment variable, or {@code null} if it is not set. Null is
     *         not an error here — an entry may be catalogued before its key exists — so the caller
     *         leaves the stored credential untouched rather than clearing it.
     */
    String resolve(String envName);
}
