package io.mateu.ecdemo1.iacp.application.out.query.dto;

import io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetPeriod;
import io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetScope;

import java.time.LocalDateTime;

public record BudgetDto(String id, String name, BudgetScope scope, String subjectId,
                        BudgetPeriod period, long limitTokens, boolean enabled,
                        LocalDateTime created) {
}
