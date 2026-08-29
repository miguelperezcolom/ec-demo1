package io.mateu.ecdemo1.iacp.infra.out.usage;

import io.mateu.ecdemo1.iacp.application.out.usage.UsageEvent;
import io.mateu.ecdemo1.iacp.application.out.usage.UsageLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The usage log on the control plane's own PostgreSQL, beside the catalogues it meters. */
@Repository
@RequiredArgsConstructor
public class JpaUsageLog implements UsageLog {

    private final TokenUsageEntityRepository repository;

    @Override
    @Transactional
    public void append(UsageEvent event) {
        var entity = new TokenUsageEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setOccurredAt(Instant.now());
        entity.setAgentId(event.agentId());
        entity.setLlmId(event.llmId());
        entity.setModel(event.model());
        entity.setInputTokens(event.inputTokens());
        entity.setOutputTokens(event.outputTokens());
        entity.setTotalTokens(event.totalTokens());
        entity.setUserId(event.userId());
        entity.setUsername(event.username());
        entity.setRoles(event.roles() == null ? null : String.join(" ", event.roles()));
        entity.setTenant(event.tenant());
        entity.setSessionId(event.sessionId());
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public long spentByAgentSince(String agentId, Instant since) {
        return agentId == null ? 0 : repository.sumByAgentSince(agentId, since);
    }

    @Override
    @Transactional(readOnly = true)
    public long spentByLlmSince(String llmId, Instant since) {
        return llmId == null ? 0 : repository.sumByLlmSince(llmId, since);
    }

    @Override
    @Transactional(readOnly = true)
    public long spentByUserSince(String userId, Instant since) {
        return userId == null ? 0 : repository.sumByUserSince(userId, since);
    }

    @Override
    @Transactional(readOnly = true)
    public long spentByTenantSince(String tenant, Instant since) {
        return tenant == null || tenant.isBlank() ? 0 : repository.sumByTenantSince(tenant, since);
    }
}
