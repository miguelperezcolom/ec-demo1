package io.mateu.ecdemo1.iacp.application.usecases.budget.create;

import io.mateu.ecdemo1.iacp.application.out.repository.BudgetRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.budget.Budget;
import io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetId;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Name;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateBudgetUseCase {

    final BudgetRepository repository;

    @Transactional
    public String handle(CreateBudgetCommand command) {
        var id = new BudgetId(command.id());
        if (repository.existsById(id)) {
            throw new IllegalArgumentException("A budget with id '" + command.id() + "' already exists");
        }
        repository.save(Budget.of(id, new Name(command.name()), command.scope(),
                command.subjectId(), command.period(), command.limitTokens()));
        return id.value();
    }
}
