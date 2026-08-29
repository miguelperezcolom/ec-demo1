package io.mateu.ecdemo1.iacp.application.out.gitops.manifest;

/**
 * One routing rule as the repo declares it. The four conditions are optional — a null one means
 * "any" — and {@code targetAgent} is the id of the agent chosen when they all match.
 */
public record RouteManifest(String id, String name, Integer priority, String role, String tenant,
                            String locale, String routePrefix, String targetAgent, Boolean enabled) {
}
