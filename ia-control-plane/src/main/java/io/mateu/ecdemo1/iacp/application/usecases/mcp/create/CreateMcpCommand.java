package io.mateu.ecdemo1.iacp.application.usecases.mcp.create;

import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.vo.McpTransport;

public record CreateMcpCommand(String id, String name, String url, McpTransport transport,
                               Long timeoutSeconds, String description) {
}
