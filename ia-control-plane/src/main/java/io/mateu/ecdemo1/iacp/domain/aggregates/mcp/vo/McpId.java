package io.mateu.ecdemo1.iacp.domain.aggregates.mcp.vo;

public record McpId(String value) {
    public McpId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("An MCP id is required");
        }
    }
    @Override public String toString() { return value; }
}
