package io.mateu.ecdemo1.iacp.application.usecases.agent.delete;

import java.util.List;

public record DeleteAgentCommand(List<String> ids) {
}
