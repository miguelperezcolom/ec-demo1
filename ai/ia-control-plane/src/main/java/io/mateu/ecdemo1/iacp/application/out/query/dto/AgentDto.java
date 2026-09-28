package io.mateu.ecdemo1.iacp.application.out.query.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AgentDto(
        String id,
        String name,
        String systemPrompt,
        String llmId,
        List<String> mcpIds,
        List<String> ragIds,
        List<String> peerAgentIds,
        String description,
        boolean enabled,
        LocalDateTime created) {
}
