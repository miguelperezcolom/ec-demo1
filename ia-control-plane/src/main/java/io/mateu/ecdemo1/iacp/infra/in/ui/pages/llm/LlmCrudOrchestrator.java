package io.mateu.ecdemo1.iacp.infra.in.ui.pages.llm;

import io.mateu.core.infra.declarative.orchestrators.crud.Crud;
import io.mateu.ecdemo1.iacp.application.out.query.LlmQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.dto.LlmRow;
import io.mateu.ecdemo1.iacp.application.usecases.llm.delete.DeleteLlmCommand;
import io.mateu.ecdemo1.iacp.application.usecases.llm.delete.DeleteLlmUseCase;
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
@Title("LLMs")
public class LlmCrudOrchestrator extends Crud<
        LlmViewModel, LlmViewModel, LlmViewModel, NoFilters, LlmRow, String> {

    final LlmViewModel viewModel;
    final DeleteLlmUseCase deleteLlmUseCase;
    final LlmQueryService queryService;

    @Override
    public ListingData<LlmRow> search(SearchRequest request, HttpRequest httpRequest) {
        return queryService.findAll(request.searchText(), filters(request), request.pageable());
    }

    @Override
    public LlmViewModel view(String id, HttpRequest httpRequest) {
        return viewModel.load(queryService.getById(id).orElseThrow());
    }

    @Override
    public LlmViewModel edit(String id, HttpRequest httpRequest) {
        return viewModel.load(queryService.getById(id).orElseThrow());
    }

    @Override
    public LlmViewModel creationForm(HttpRequest httpRequest) {
        return viewModel;
    }

    @Override
    public String save(HttpRequest httpRequest) {
        var editor = httpRequest.getComponentState(LlmViewModel.class);
        editor.save(httpRequest);
        return editor.id();
    }

    @Override
    public String create(HttpRequest httpRequest) {
        return httpRequest.getComponentState(LlmViewModel.class).create(httpRequest);
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        deleteLlmUseCase.handle(new DeleteLlmCommand(selectedIds));
    }

    @Override
    public String getIdFieldForRow() {
        return "id";
    }
}
