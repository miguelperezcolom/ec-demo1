package io.mateu.ecdemo1.iacp.application.usecases.route.update;

public record UpdateRouteCommand(String id, String name, int priority, String role, String tenant,
                                 String locale, String routePrefix, String targetAgentId,
                                 boolean enabled) {
}
