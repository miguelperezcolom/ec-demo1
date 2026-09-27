package io.mateu.ecdemo1.iacp.application.out.repository;

import io.mateu.ecdemo1.iacp.domain.aggregates.budget.Budget;
import io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetId;

public interface BudgetRepository extends Repository<Budget, BudgetId> {
}
