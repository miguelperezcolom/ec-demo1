package io.mateu.ecdemo1.iacp.application.usecases.budget.delete;

import io.mateu.ecdemo1.iacp.application.out.repository.BudgetRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteBudgetUseCase {

    final BudgetRepository repository;

    @Transactional
    public void handle(DeleteBudgetCommand command) {
        repository.deleteAllById(command.ids().stream().map(BudgetId::new).toList());
    }
}
