package io.mateu.ecdemo1.iacp.infra.out.usage;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One prompt's token cost, as the agent reported it. An append-only log: the control plane records
 * what was spent, aggregates it for budgets, and exports it to Prometheus, but never edits a row.
 *
 * <p>It carries the subject as well as the numbers — which agent, which model, and who asked
 * ({@code userId}, {@code roles}, {@code tenant}) — because a budget can be scoped to any of them,
 * and a sum is only as specific as the columns it can group by. {@code occurredAt} is indexed
 * because every budget check is "how much since the start of the period", a range scan on time.
 */
@Entity
@Table(name = "token_usage", indexes = {
        @Index(name = "idx_token_usage_time", columnList = "occurredAt"),
        @Index(name = "idx_token_usage_agent", columnList = "agentId"),
        @Index(name = "idx_token_usage_llm", columnList = "llmId")
})
@Getter
@Setter
@NoArgsConstructor
public class TokenUsageEntity {

    @Id
    String id;

    Instant occurredAt;

    String agentId;
    String llmId;
    String model;

    int inputTokens;
    int outputTokens;
    int totalTokens;

    /** Who asked, as the agent read it from the token. Any of these can be a budget's subject. */
    String userId;
    String username;
    /** Space-separated realm roles, kept as text — this is a log, not a normalized model. */
    String roles;
    String tenant;

    String sessionId;
}
