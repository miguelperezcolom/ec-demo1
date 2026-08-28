package io.mateu.ecdemo1.iacp.domain.aggregates.agent.vo;

/**
 * The instructions an agent is given before anything a user says.
 *
 * <p>This is the highest-leverage field in the whole catalogue and it does not look like it. It
 * decides whether the agent refuses a failed tool call or invents an answer around it, and it is
 * editable from a web form by anyone who can reach the control console — which is why that console
 * is behind the {@code admin} realm role and not merely behind a login.
 */
public record SystemPrompt(String value) {
    public SystemPrompt {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A system prompt is required");
        }
    }
    @Override public String toString() { return value; }
}
