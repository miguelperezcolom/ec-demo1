package io.mateu.ecdemo1.iacp.infra.out.usage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface TokenUsageEntityRepository extends JpaRepository<TokenUsageEntity, String> {

    /**
     * Total tokens an agent has spent since {@code since}. Null when there are no rows, which the
     * caller reads as zero. Used by the budget check.
     */
    @Query("select coalesce(sum(u.totalTokens), 0) from TokenUsageEntity u "
            + "where u.agentId = :agentId and u.occurredAt >= :since")
    long sumByAgentSince(@Param("agentId") String agentId, @Param("since") Instant since);

    @Query("select coalesce(sum(u.totalTokens), 0) from TokenUsageEntity u "
            + "where u.llmId = :llmId and u.occurredAt >= :since")
    long sumByLlmSince(@Param("llmId") String llmId, @Param("since") Instant since);

    @Query("select coalesce(sum(u.totalTokens), 0) from TokenUsageEntity u "
            + "where u.userId = :userId and u.occurredAt >= :since")
    long sumByUserSince(@Param("userId") String userId, @Param("since") Instant since);

    @Query("select coalesce(sum(u.totalTokens), 0) from TokenUsageEntity u "
            + "where u.tenant = :tenant and u.occurredAt >= :since")
    long sumByTenantSince(@Param("tenant") String tenant, @Param("since") Instant since);
}
