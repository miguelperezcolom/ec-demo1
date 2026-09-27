package io.mateu.ecdemo1.iacp.infra.out.persistence;

import io.mateu.ecdemo1.iacp.application.out.repository.BudgetRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.budget.Budget;
import io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetId;
import io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetPeriod;
import io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetScope;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Enabled;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Name;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Time;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BudgetDBRepository implements BudgetRepository {

    final BudgetEntityRepository entities;

    @Override
    public Budget save(Budget budget) {
        var e = entities.findById(budget.getId().value()).orElseGet(BudgetEntity::new);
        e.setId(budget.getId().value());
        e.setName(budget.getName().value());
        e.setScope(budget.getScope().name());
        e.setSubjectId(budget.getSubjectId());
        e.setPeriod(budget.getPeriod().name());
        e.setLimitTokens(budget.getLimitTokens());
        e.setEnabled(budget.getEnabled().value());
        e.setCreated(budget.getCreated().value());
        entities.save(e);
        return budget;
    }

    @Override
    public Optional<Budget> findById(BudgetId id) {
        return entities.findById(id.value()).map(BudgetDBRepository::toDomain);
    }

    @Override
    public List<Budget> findAll() {
        return entities.findAll().stream().map(BudgetDBRepository::toDomain).toList();
    }

    @Override
    public void deleteAllById(List<BudgetId> ids) {
        entities.deleteAllById(ids.stream().map(BudgetId::value).toList());
    }

    @Override
    public boolean existsById(BudgetId id) {
        return entities.existsById(id.value());
    }

    static Budget toDomain(BudgetEntity e) {
        return new Budget(
                new BudgetId(e.getId()),
                new Name(e.getName()),
                BudgetScope.valueOf(e.getScope()),
                e.getSubjectId(),
                BudgetPeriod.valueOf(e.getPeriod()),
                e.getLimitTokens(),
                new Enabled(e.isEnabled()),
                new Time(e.getCreated()));
    }
}
