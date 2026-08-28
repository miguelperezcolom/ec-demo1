package io.mateu.ecdemo1.iacp.domain.aggregates.mcp.vo;

/**
 * How a client reaches this server.
 *
 * <p>SSE is what every MCP server in this deployment speaks today — the orchestrator, the forms
 * engine and the booking service all expose {@code /sse}. STREAMABLE_HTTP is the newer transport;
 * cataloguing it is allowed so an entry can be written ahead of the client supporting it.
 */
public enum McpTransport {
    SSE,
    STREAMABLE_HTTP
}
