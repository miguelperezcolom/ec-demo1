package io.mateu.ecdemo1.iacp.application.out.gitops.manifest;

import io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetPeriod;
import io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetScope;

/**
 * One spend cap as the repo declares it. {@code subject} is the id the {@code scope} points at — an
 * agent, model, user or tenant — kept as a plain field because the thing it names lives in another
 * catalogue (or in Keycloak), which the reconciler does not cross-check.
 */
public record BudgetManifest(String id, String name, BudgetScope scope, String subject,
                             BudgetPeriod period, long limitTokens, Boolean enabled) {
}
