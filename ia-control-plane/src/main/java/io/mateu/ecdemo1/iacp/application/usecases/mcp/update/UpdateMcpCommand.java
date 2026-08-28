package io.mateu.ecdemo1.iacp.application.usecases.mcp.update;

import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.vo.McpTransport;

public record UpdateMcpCommand(String id, String name, String url, McpTransport transport,
                               Long timeoutSeconds, String description, boolean enabled) {
}
