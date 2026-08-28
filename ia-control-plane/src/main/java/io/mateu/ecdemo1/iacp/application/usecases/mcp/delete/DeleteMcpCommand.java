package io.mateu.ecdemo1.iacp.application.usecases.mcp.delete;

import java.util.List;

public record DeleteMcpCommand(List<String> ids) {
}
