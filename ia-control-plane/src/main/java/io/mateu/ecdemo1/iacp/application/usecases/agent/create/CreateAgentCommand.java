package io.mateu.ecdemo1.iacp.application.usecases.agent.create;

import java.util.List;

public record CreateAgentCommand(String id, String name, String systemPrompt, String llmId,
                                 List<String> mcpIds, List<String> ragIds, String description) {
}
