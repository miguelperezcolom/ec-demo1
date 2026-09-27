package io.mateu.ecdemo1.iacp.infra.out.persistence;

import io.mateu.ecdemo1.iacp.application.out.query.BudgetQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.dto.BudgetDto;
import io.mateu.ecdemo1.iacp.application.out.query.dto.BudgetRow;
import io.mateu.ecdemo1.iacp.application.out.query.dto.StatusBadge;
import io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetPeriod;
import io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetScope;
import io.mateu.ecdemo1.uicommons.paging.DbPaging;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BudgetDBQueryService implements BudgetQueryService {

    final BudgetEntityRepository entities;

    @Override
    public ListingData<BudgetRow> findAll(String searchText, Object filters, Pageable pageable) {
        var springPageable = DbPaging.pageable(pageable);
        var page = searchText == null || searchText.isBlank()
                ? entities.findAll(springPageable)
                : entities.findByNameContainingIgnoreCase(searchText, springPageable);
        return DbPaging.listing(searchText, page, BudgetDBQueryService::toRow);
    }

    @Override
    public String getLabel(String id) {
        return entities.findById(id).map(BudgetEntity::getName).orElse("Unknown");
    }

    @Override
    public Optional<BudgetDto> getById(String id) {
        return entities.findById(id).map(BudgetDBQueryService::toDto);
    }

    @Override
    public List<BudgetDto> all() {
        return entities.findAll().stream().map(BudgetDBQueryService::toDto).toList();
    }

    @Override
    public long count() {
        return entities.count();
    }

    @Override
    public long countEnabled() {
        return entities.countByEnabledTrue();
    }

    static BudgetDto toDto(BudgetEntity e) {
        return new BudgetDto(e.getId(), e.getName(), BudgetScope.valueOf(e.getScope()),
                e.getSubjectId(), BudgetPeriod.valueOf(e.getPeriod()), e.getLimitTokens(),
                e.isEnabled(), e.getCreated());
    }

    static BudgetRow toRow(BudgetEntity e) {
        return new BudgetRow(e.getId(), e.getName(), e.getScope(), e.getSubjectId(),
                e.getPeriod(), e.getLimitTokens(), StatusBadge.enabled(e.isEnabled()));
    }
}
