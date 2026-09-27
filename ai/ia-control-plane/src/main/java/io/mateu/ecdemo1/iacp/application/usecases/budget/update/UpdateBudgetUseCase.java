package io.mateu.ecdemo1.iacp.application.usecases.budget.update;

import io.mateu.ecdemo1.iacp.application.out.repository.BudgetRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetId;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Enabled;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Name;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateBudgetUseCase {

    final BudgetRepository repository;

    @Transactional
    public void handle(UpdateBudgetCommand command) {
        var budget = repository.findById(new BudgetId(command.id()))
                .orElseThrow(() -> new IllegalArgumentException("No budget with id '" + command.id() + "'"));
        budget.update(new Name(command.name()), command.scope(), command.subjectId(),
                command.period(), command.limitTokens(), new Enabled(command.enabled()));
        repository.save(budget);
    }
}
