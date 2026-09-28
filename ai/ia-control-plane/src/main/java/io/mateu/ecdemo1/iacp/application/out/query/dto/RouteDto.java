package io.mateu.ecdemo1.iacp.application.out.query.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RouteDto(String id, String name, int priority, String role, String tenant,
                       String locale, String routePrefix, String targetAgentId,
                       List<String> inputGuardrailAgentIds, List<String> outputGuardrailAgentIds,
                       String guardrailFailure, boolean enabled,
                       LocalDateTime created) {
}
