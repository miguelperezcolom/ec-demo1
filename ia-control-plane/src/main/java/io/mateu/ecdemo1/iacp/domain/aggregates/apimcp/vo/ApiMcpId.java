package io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo;

public record ApiMcpId(String value) {
    public ApiMcpId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("An API MCP id is required");
        }
    }
    @Override public String toString() { return value; }
}
