package io.mateu.ecdemo1.iacp.application.out.query.dto;

import io.mateu.ecdemo1.iacp.domain.aggregates.rag.vo.RagKind;

import java.time.LocalDateTime;

public record RagDto(
        String id,
        String name,
        RagKind kind,
        String connectionUrl,
        String collection,
        String embeddingLlmId,
        int topK,
        String description,
        boolean enabled,
        LocalDateTime created) {
}
