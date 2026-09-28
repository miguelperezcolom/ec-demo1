package io.mateu.ecdemo1.iacp.domain.aggregates.route.vo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * The agents a route puts around the agent it picks: {@code input} ones read the user's text before
 * the agent does, {@code output} ones read the agent's answer before the user does. Each is an agent
 * of the catalogue, called over A2A, answering a verdict — allow, block, or rewrite.
 *
 * <p>Ordered, because they run in the order given and a rewrite by the first is what the second
 * reads. Deduplicated, because running one guardrail twice on the same text can only cost.
 *
 * <p>{@code failure} is what a guardrail that cannot give a verdict — unreachable, or answering
 * something that is not one — amounts to: {@link Failure#CLOSED} blocks, which is the default
 * because a guardrail that silently stops guarding is worse than a refused prompt;
 * {@link Failure#OPEN} lets the text through and logs it.
 */
public record Guardrails(List<String> input, List<String> output, Failure failure) {

    public enum Failure {
        CLOSED, OPEN;

        /** Blank is the default, CLOSED; anything else must be one of the two. */
        public static Failure parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return CLOSED;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("guardrailFailure must be CLOSED or OPEN, not '" + raw + "'");
            }
        }
    }

    public Guardrails {
        input = clean(input);
        output = clean(output);
        failure = failure == null ? Failure.CLOSED : failure;
    }

    public static Guardrails none() {
        return new Guardrails(List.of(), List.of(), Failure.CLOSED);
    }

    public boolean mentions(String agentId) {
        return input.contains(agentId) || output.contains(agentId);
    }

    private static List<String> clean(List<String> ids) {
        if (ids == null) {
            return List.of();
        }
        return List.copyOf(new ArrayList<>(new LinkedHashSet<>(
                ids.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList())));
    }
}
