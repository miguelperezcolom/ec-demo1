package io.mateu.ecdemo1.iacp.domain.aggregates.llm.vo;

public record LlmId(String value) {
    public LlmId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("An LLM id is required");
        }
    }
    @Override public String toString() { return value; }
}
