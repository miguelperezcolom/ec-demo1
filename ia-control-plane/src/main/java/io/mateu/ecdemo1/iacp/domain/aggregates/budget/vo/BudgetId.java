package io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo;

public record BudgetId(String value) {
    public BudgetId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A budget id is required");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
