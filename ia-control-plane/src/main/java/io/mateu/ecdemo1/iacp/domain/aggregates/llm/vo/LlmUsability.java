package io.mateu.ecdemo1.iacp.domain.aggregates.llm.vo;

/**
 * Why a catalogued model can or cannot be served — not merely whether.
 *
 * <p>This exists because a boolean was not enough and everyone who needed more than a boolean
 * guessed. {@code ResolveAgentConfigUseCase} told an operator their model was "missing its
 * credential" when the key was stored and the provider was the problem, because "not enabled" and
 * "not credentialled" were the only two cases its message knew about. The listing, computing the
 * same idea a second time from the entity, called a Bedrock entry with a key "usable" while the
 * control plane refused to serve it — the console asserting the opposite of what the agent got.
 *
 * <p>So the reason is the value now, and {@code isUsable} is derived from it rather than the other
 * way round. One computation, from the three facts that decide it, used by both the domain and the
 * read side.
 */
public enum LlmUsability {

    USABLE("usable"),

    /** Turned off deliberately. Says nothing about whether it would otherwise work. */
    DISABLED("disabled"),

    /**
     * A provider whose credential is not a single API key — Bedrock, Vertex. Nothing here speaks
     * them yet, so no credential makes this entry servable and asking for one is a wrong turn.
     */
    PROVIDER_NOT_SUPPORTED("provider not supported"),

    /** An API-key provider with no key. The one case that a credential actually fixes. */
    NO_CREDENTIAL("no credential");

    private final String label;

    LlmUsability(String label) {
        this.label = label;
    }

    /** What the catalogue listing shows in its status column. */
    public String label() {
        return label;
    }

    public boolean isUsable() {
        return this == USABLE;
    }

    /**
     * The order is the answer's order, and it is not arbitrary.
     *
     * <p>Disabled comes first because it is a decision somebody made, and reporting anything else
     * about an entry that was switched off answers a question nobody asked. The provider comes
     * before the credential because it outranks it: a Bedrock entry is unservable whether or not a
     * key is stored, so "no credential" there would send an operator to paste a key that changes
     * nothing — which is exactly what happened.
     */
    public static LlmUsability of(boolean enabled, LlmProvider provider, boolean credentialSet) {
        if (!enabled) {
            return DISABLED;
        }
        if (!provider.isApiKeyBased()) {
            return PROVIDER_NOT_SUPPORTED;
        }
        return credentialSet ? USABLE : NO_CREDENTIAL;
    }
}
