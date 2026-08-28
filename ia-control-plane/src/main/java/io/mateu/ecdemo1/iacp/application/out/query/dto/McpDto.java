package io.mateu.ecdemo1.iacp.application.out.query.dto;

import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.vo.McpTransport;

import java.time.LocalDateTime;

public record McpDto(
        String id,
        String name,
        String url,
        McpTransport transport,
        long timeoutSeconds,
        String description,
        boolean enabled,
        LocalDateTime created) {
}
