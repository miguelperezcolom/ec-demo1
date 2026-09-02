package io.mateu.ecdemo1.iacp.application.out.query.dto;

import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ApiKind;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One API-backed MCP server, as everything outside the domain reads it.
 *
 * <p>The credential is a boolean and never the value — the same rule the LLM catalogue follows, for
 * the same reason: nothing outside the one method that decrypts should be able to carry it.
 */
public record ApiMcpDto(
        String id,
        String name,
        ApiKind kind,
        String baseUrl,
        String specUrl,
        boolean credentialSet,
        List<ExposedToolDto> tools,
        String description,
        boolean enabled,
        LocalDateTime created) {

    public record ExposedToolDto(String operation, String toolName, String description,
                                 List<String> requiredRoles) {
    }
}
