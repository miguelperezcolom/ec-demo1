package io.mateu.ecdemo1.iacp.infra.in.ui.pages.apimcp;

import io.mateu.core.infra.declarative.orchestrators.crud.Crud;
import io.mateu.ecdemo1.iacp.application.out.query.ApiMcpQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.dto.ApiMcpRow;
import io.mateu.ecdemo1.iacp.application.usecases.apimcp.delete.DeleteApiMcpCommand;
import io.mateu.ecdemo1.iacp.application.usecases.apimcp.delete.DeleteApiMcpUseCase;
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
@Title("APIs as MCP servers")
public class ApiMcpCrudOrchestrator extends Crud<
        ApiMcpViewModel, ApiMcpViewModel, ApiMcpViewModel, NoFilters, ApiMcpRow, String> {

    final ApiMcpViewModel viewModel;
    final DeleteApiMcpUseCase deleteApiMcpUseCase;
    final ApiMcpQueryService queryService;

    @Override
    public ListingData<ApiMcpRow> search(SearchRequest request, HttpRequest httpRequest) {
        return queryService.findAll(request.searchText(), filters(request), request.pageable());
    }

    @Override
    public ApiMcpViewModel view(String id, HttpRequest httpRequest) {
        return viewModel.load(queryService.getById(id).orElseThrow());
    }

    @Override
    public ApiMcpViewModel edit(String id, HttpRequest httpRequest) {
        return viewModel.load(queryService.getById(id).orElseThrow());
    }

    @Override
    public ApiMcpViewModel creationForm(HttpRequest httpRequest) {
        return viewModel;
    }

    @Override
    public String save(HttpRequest httpRequest) {
        var editor = httpRequest.getComponentState(ApiMcpViewModel.class);
        editor.save(httpRequest);
        return editor.id();
    }

    @Override
    public String create(HttpRequest httpRequest) {
        return httpRequest.getComponentState(ApiMcpViewModel.class).create(httpRequest);
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        deleteApiMcpUseCase.handle(new DeleteApiMcpCommand(selectedIds));
    }

    @Override
    public String getIdFieldForRow() {
        return "id";
    }
}
