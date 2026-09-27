package io.mateu.ecdemo1.iacp.application.usecases.agent.update;

import java.util.List;

public record UpdateAgentCommand(String id, String name, String systemPrompt, String llmId,
                                 List<String> mcpIds, List<String> ragIds, String description,
                                 boolean enabled) {
}
