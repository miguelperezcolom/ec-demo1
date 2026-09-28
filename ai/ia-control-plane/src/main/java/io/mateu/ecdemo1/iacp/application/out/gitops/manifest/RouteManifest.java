package io.mateu.ecdemo1.iacp.application.out.gitops.manifest;

import java.util.List;

/**
 * One routing rule as the repo declares it. The four conditions are optional — a null one means
 * "any" — and {@code targetAgent} is the id of the agent chosen when they all match.
 * {@code inputGuardrails} and {@code outputGuardrails} are agent ids, run in order around it, and
 * {@code guardrailFailure} is CLOSED (the default) or OPEN.
 */
public record RouteManifest(String id, String name, Integer priority, String role, String tenant,
                            String locale, String routePrefix, String targetAgent,
                            List<String> inputGuardrails, List<String> outputGuardrails,
                            String guardrailFailure, Boolean enabled) {
}
