package io.mateu.ecdemo1.iacp.application.usecases.apimcp.delete;

import java.util.List;

public record DeleteApiMcpCommand(List<String> ids) {
}
