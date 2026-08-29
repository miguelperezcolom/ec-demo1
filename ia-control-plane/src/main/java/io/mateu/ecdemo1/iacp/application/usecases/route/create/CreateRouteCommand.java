package io.mateu.ecdemo1.iacp.application.usecases.route.create;

public record CreateRouteCommand(String id, String name, int priority, String role, String tenant,
                                 String locale, String routePrefix, String targetAgentId) {
}
