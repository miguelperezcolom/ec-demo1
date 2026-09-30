package io.mateu.ecdemo1.iacp.application.usecases.route.update;

import java.util.List;

public record UpdateRouteCommand(String id, String name, int priority, String role, String tenant,
                                 String locale, String routePrefix, String channel, String targetAgentId,
                                 List<String> inputGuardrailAgentIds,
                                 List<String> outputGuardrailAgentIds, String guardrailFailure,
                                 boolean enabled) {
}
