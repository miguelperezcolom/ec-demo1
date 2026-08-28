package io.mateu.ecdemo1.iacp.application.usecases.rag.create;

import io.mateu.ecdemo1.iacp.domain.aggregates.rag.vo.RagKind;

public record CreateRagCommand(String id, String name, RagKind kind, String connectionUrl,
                               String collection, String embeddingLlmId, Integer topK,
                               String description) {
}
