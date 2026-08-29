package io.mateu.ecdemo1.iacp.application.out.usage;

import java.time.Instant;

/**
 * The append-only record of token spend, and the sums a budget check reads from it.
 *
 * <p>Append and sum are on the same port because they are the same data seen two ways: the agent
 * writes one row per prompt, and the budget check asks "how much of {@code subject} since
 * {@code since}". Keeping the sums here rather than in a query service is deliberate — they are not
 * a listing for a screen, they are the enforcement's own read, and they belong next to the write
 * they enforce against.
 */
public interface UsageLog {

    /** Record one prompt's cost. Never fails a prompt — the caller reports after answering. */
    void append(UsageEvent event);

    long spentByAgentSince(String agentId, Instant since);

    long spentByLlmSince(String llmId, Instant since);

    long spentByUserSince(String userId, Instant since);

    long spentByTenantSince(String tenant, Instant since);

    /**
     * Delete usage older than {@code retention}, bounding the log. The retention must exceed the
     * longest budget window, or a monthly budget would sum a month it has already forgotten the
     * start of.
     *
     * @return how many rows were removed
     */
    int purgeOlderThan(java.time.Duration retention);
}
