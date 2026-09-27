package io.mateu.ecdemo1.iacp.infra.in.ui.pages.budget;

import io.mateu.core.infra.declarative.orchestrators.crud.Crud;
import io.mateu.ecdemo1.iacp.application.out.query.BudgetQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.dto.BudgetRow;
import io.mateu.ecdemo1.iacp.application.usecases.budget.delete.DeleteBudgetCommand;
import io.mateu.ecdemo1.iacp.application.usecases.budget.delete.DeleteBudgetUseCase;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.NoFilters;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Scope("prototype")
@Title("Budgets")
public class BudgetCrudOrchestrator extends Crud<
        BudgetViewModel, BudgetViewModel, BudgetViewModel, NoFilters, BudgetRow, String> {

    final BudgetViewModel viewModel;
    final DeleteBudgetUseCase deleteBudgetUseCase;
    final BudgetQueryService queryService;

    @Override
    public ListingData<BudgetRow> search(SearchRequest request, HttpRequest httpRequest) {
        return queryService.findAll(request.searchText(), filters(request), request.pageable());
    }

    @Override
    public BudgetViewModel view(String id, HttpRequest httpRequest) {
        return viewModel.load(queryService.getById(id).orElseThrow());
    }

    @Override
    public BudgetViewModel edit(String id, HttpRequest httpRequest) {
        return viewModel.load(queryService.getById(id).orElseThrow());
    }

    @Override
    public BudgetViewModel creationForm(HttpRequest httpRequest) {
        return viewModel;
    }

    @Override
    public String save(HttpRequest httpRequest) {
        var editor = httpRequest.getComponentState(BudgetViewModel.class);
        editor.save(httpRequest);
        return editor.id();
    }

    @Override
    public String create(HttpRequest httpRequest) {
        return httpRequest.getComponentState(BudgetViewModel.class).create(httpRequest);
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        deleteBudgetUseCase.handle(new DeleteBudgetCommand(selectedIds));
    }

    @Override
    public String getIdFieldForRow() {
        return "id";
    }
}
