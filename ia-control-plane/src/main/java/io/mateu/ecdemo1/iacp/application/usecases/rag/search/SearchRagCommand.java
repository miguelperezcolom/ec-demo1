package io.mateu.ecdemo1.iacp.application.usecases.rag.search;

public record SearchRagCommand(String ragId, String query, Integer topK) {
}
