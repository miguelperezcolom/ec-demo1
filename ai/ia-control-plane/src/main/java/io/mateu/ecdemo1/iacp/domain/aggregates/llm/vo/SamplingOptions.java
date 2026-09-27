package io.mateu.ecdemo1.iacp.domain.aggregates.llm.vo;

/**
 * What the catalogue fixes about how this model is sampled.
 *
 * <p>Both are bounded here rather than at the edge, because both are ways to spend money or to
 * make an agent useless and neither fails loudly: a max-tokens of 30 truncates every answer
 * mid-sentence, and a temperature of 2 makes a tool-calling agent improvise arguments.
 */
public record SamplingOptions(Double temperature, Integer maxTokens) {
    public SamplingOptions {
        if (temperature != null && (temperature < 0.0 || temperature > 1.0)) {
            throw new IllegalArgumentException("Temperature must be between 0 and 1");
        }
        if (maxTokens != null && maxTokens < 256) {
            throw new IllegalArgumentException("Max tokens must be at least 256");
        }
    }
    public static SamplingOptions defaults() { return new SamplingOptions(0.1, 4096); }
}
