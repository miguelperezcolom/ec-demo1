package io.mateu.ecdemo1.iacp.application.usecases.budget.delete;

import java.util.List;

public record DeleteBudgetCommand(List<String> ids) {
}
