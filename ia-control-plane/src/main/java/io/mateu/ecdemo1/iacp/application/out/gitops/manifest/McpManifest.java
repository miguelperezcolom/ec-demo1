package io.mateu.ecdemo1.iacp.application.out.gitops.manifest;

import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.vo.McpTransport;

/** One MCP server entry as the repo declares it. */
public record McpManifest(String id, String name, String url, McpTransport transport,
                          Long timeoutSeconds, String description, Boolean enabled) {
}
