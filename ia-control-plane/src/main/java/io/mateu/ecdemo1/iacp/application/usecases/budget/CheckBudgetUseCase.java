package io.mateu.ecdemo1.iacp.application.usecases.budget;

import io.mateu.ecdemo1.iacp.application.out.repository.BudgetRepository;
import io.mateu.ecdemo1.iacp.application.out.usage.UsageLog;
import io.mateu.ecdemo1.iacp.domain.aggregates.budget.Budget;
import io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetPeriod;
import io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Decides whether a prompt may be served, given who and what would spend on it.
 *
 * <p>It reads every enabled budget, keeps the ones whose subject the request actually has — a
 * per-user budget only applies when there is a user — and for each of those sums the spend in the
 * current window and compares it to the limit. The <em>first</em> exceeded budget denies the whole
 * request: caps do not average, the tightest one that has been reached is the one that bites. A
 * request that matches no budget is allowed, which is the point of budgets being opt-in — nothing
 * is capped until someone caps it.
 *
 * <p>The comparison is {@code spent >= limit}, so the limit is the last tokens that may be spent,
 * not the first that may not. Because spend is summed after the fact and a prompt's own cost is not
 * yet in the sum when it is checked, enforcement is "no new prompt once the line is crossed" rather
 * than a hard byte ceiling — the overshoot is one prompt, which for a token cap is the right
 * precision.
 */
@Service
@RequiredArgsConstructor
public class CheckBudgetUseCase {

    private final BudgetRepository budgets;
    private final UsageLog usage;

    public record Exceeded(String budgetId, String name, BudgetScope scope, String subjectId,
                           BudgetPeriod period, long limit, long spent) {
    }

    public record Verdict(boolean allowed, Exceeded exceeded) {
        static Verdict ok() {
            return new Verdict(true, null);
        }

        static Verdict denied(Exceeded e) {
            return new Verdict(false, e);
        }
    }

    @Transactional(readOnly = true)
    public Verdict check(String agentId, String llmId, String userId, String tenant) {
        var now = Instant.now();
        for (Budget budget : budgets.findAll()) {
            if (!budget.isUsable()) {
                continue;
            }
            var subject = subjectFor(budget.getScope(), agentId, llmId, userId, tenant);
            if (subject == null || !subject.equals(budget.getSubjectId())) {
                continue;
            }
            var since = budget.getPeriod().currentWindowStart(now);
            long spent = spent(budget.getScope(), subject, since);
            if (spent >= budget.getLimitTokens()) {
                return Verdict.denied(new Exceeded(budget.getId().value(), budget.getName().value(),
                        budget.getScope(), subject, budget.getPeriod(), budget.getLimitTokens(), spent));
            }
        }
        return Verdict.ok();
    }

    private static String subjectFor(BudgetScope scope, String agentId, String llmId,
                                     String userId, String tenant) {
        return switch (scope) {
            case AGENT -> agentId;
            case LLM -> llmId;
            case USER -> userId;
            case TENANT -> tenant;
        };
    }

    private long spent(BudgetScope scope, String subject, Instant since) {
        return switch (scope) {
            case AGENT -> usage.spentByAgentSince(subject, since);
            case LLM -> usage.spentByLlmSince(subject, since);
            case USER -> usage.spentByUserSince(subject, since);
            case TENANT -> usage.spentByTenantSince(subject, since);
        };
    }
}
