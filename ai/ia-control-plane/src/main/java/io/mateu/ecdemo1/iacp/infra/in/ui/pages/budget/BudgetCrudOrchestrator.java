package io.mateu.ecdemo1.iacp.infra.in.ui.pages.budget;

import io.mateu.ecdemo1.iacp.application.out.query.BudgetQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.dto.BudgetDto;
import io.mateu.ecdemo1.iacp.application.out.query.dto.BudgetRow;
import io.mateu.ecdemo1.iacp.application.usecases.budget.delete.DeleteBudgetCommand;
import io.mateu.ecdemo1.iacp.application.usecases.budget.delete.DeleteBudgetUseCase;
import io.mateu.ecdemo1.uicommons.crud.CatalogueCrud;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.NoFilters;
import io.mateu.uidl.interfaces.HttpRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

/** The CRUD of this catalogue: listing, form and queries in {@link CatalogueCrud}; deleting here. */
@Service
@RequiredArgsConstructor
@Scope("prototype")
@Title("Budgets")
public class BudgetCrudOrchestrator extends CatalogueCrud<
        BudgetViewModel, BudgetViewModel, BudgetViewModel, NoFilters, BudgetRow, String, BudgetDto> {

    final BudgetViewModel viewModel;
    final DeleteBudgetUseCase deleteBudgetUseCase;
    final BudgetQueryService queryService;

    @Override
    protected BudgetViewModel editor() {
        return viewModel;
    }

    @Override
    protected BudgetQueryService queries() {
        return queryService;
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        deleteBudgetUseCase.handle(new DeleteBudgetCommand(selectedIds));
    }
}
