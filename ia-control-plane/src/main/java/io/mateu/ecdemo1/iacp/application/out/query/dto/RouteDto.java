package io.mateu.ecdemo1.iacp.application.out.query.dto;

import java.time.LocalDateTime;

public record RouteDto(String id, String name, int priority, String role, String tenant,
                       String locale, String routePrefix, String targetAgentId, boolean enabled,
                       LocalDateTime created) {
}
