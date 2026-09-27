package io.mateu.ecdemo1.iacp.domain.aggregates.llm.vo;

/**
 * Who serves the model.
 *
 * <p>It decides two things the catalogue cannot guess: the shape of the base URL when none is
 * given, and what a credential even is — Anthropic and OpenAI take an API key, Bedrock and Vertex
 * take cloud credentials that are not a single string at all. Only the key-based ones are
 * supported today; the other two are listed so a catalogue entry can be written for them before
 * the agent can use them, and rejected clearly rather than half-working.
 */
public enum LlmProvider {
    ANTHROPIC(true),
    OPENAI(true),
    /** An OpenAI-compatible endpoint that is not OpenAI: Ollama, vLLM, a gateway. */
    OPENAI_COMPATIBLE(true),
    BEDROCK(false),
    VERTEX(false);

    private final boolean apiKeyBased;

    LlmProvider(boolean apiKeyBased) { this.apiKeyBased = apiKeyBased; }

    /** True when a single API key is the whole of this provider's credential. */
    public boolean isApiKeyBased() { return apiKeyBased; }
}
