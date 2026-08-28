package io.mateu.ecdemo1.iacp.domain.aggregates.rag.vo;

public record RagId(String value) {
    public RagId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A RAG id is required");
        }
    }
    @Override public String toString() { return value; }
}
