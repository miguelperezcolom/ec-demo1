package io.mateu.ecdemo1.iacp.application.usecases.budget.update;

import io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetPeriod;
import io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetScope;

public record UpdateBudgetCommand(String id, String name, BudgetScope scope, String subjectId,
                                  BudgetPeriod period, long limitTokens, boolean enabled) {
}
