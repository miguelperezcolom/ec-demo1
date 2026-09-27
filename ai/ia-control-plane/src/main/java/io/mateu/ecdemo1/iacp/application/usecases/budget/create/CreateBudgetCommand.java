package io.mateu.ecdemo1.iacp.application.usecases.budget.create;

import io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetPeriod;
import io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetScope;

public record CreateBudgetCommand(String id, String name, BudgetScope scope, String subjectId,
                                  BudgetPeriod period, long limitTokens) {
}
