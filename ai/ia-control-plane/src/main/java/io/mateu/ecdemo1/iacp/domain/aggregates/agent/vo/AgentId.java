package io.mateu.ecdemo1.iacp.domain.aggregates.agent.vo;

public record AgentId(String value) {
    public AgentId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("An agent id is required");
        }
    }
    @Override public String toString() { return value; }
}
