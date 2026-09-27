package io.mateu.ecdemo1.iacp.application.out.usage;

import java.util.List;

/**
 * One prompt's token cost and who it was for, as the agent reports it. The subject fields —
 * {@code userId}, {@code roles}, {@code tenant} — travel with the numbers because a budget can be
 * scoped to any of them, and what is not recorded cannot later be summed.
 */
public record UsageEvent(String agentId, String llmId, String model,
                         int inputTokens, int outputTokens, int totalTokens,
                         String userId, String username, List<String> roles, String tenant,
                         String sessionId) {
}
