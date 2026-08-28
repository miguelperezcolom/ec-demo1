package io.mateu.ecdemo1.iacp.infra.in.ui.pages.agent;

import io.mateu.core.infra.declarative.orchestrators.crud.Crud;
import io.mateu.ecdemo1.iacp.application.out.query.AgentQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.dto.AgentRow;
import io.mateu.ecdemo1.iacp.application.usecases.agent.delete.DeleteAgentCommand;
import io.mateu.ecdemo1.iacp.application.usecases.agent.delete.DeleteAgentUseCase;
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
@Title("Agents")
public class AgentCrudOrchestrator extends Crud<
        AgentViewModel, AgentViewModel, AgentViewModel, NoFilters, AgentRow, String> {

    final AgentViewModel viewModel;
    final DeleteAgentUseCase deleteAgentUseCase;
    final AgentQueryService queryService;

    @Override
    public ListingData<AgentRow> search(SearchRequest request, HttpRequest httpRequest) {
        return queryService.findAll(request.searchText(), filters(request), request.pageable());
    }

    @Override
    public AgentViewModel view(String id, HttpRequest httpRequest) {
        return viewModel.load(queryService.getById(id).orElseThrow());
    }

    @Override
    public AgentViewModel edit(String id, HttpRequest httpRequest) {
        return viewModel.load(queryService.getById(id).orElseThrow());
    }

    @Override
    public AgentViewModel creationForm(HttpRequest httpRequest) {
        return viewModel;
    }

    @Override
    public String save(HttpRequest httpRequest) {
        var editor = httpRequest.getComponentState(AgentViewModel.class);
        editor.save(httpRequest);
        return editor.id();
    }

    @Override
    public String create(HttpRequest httpRequest) {
        return httpRequest.getComponentState(AgentViewModel.class).create(httpRequest);
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        deleteAgentUseCase.handle(new DeleteAgentCommand(selectedIds));
    }

    @Override
    public String getIdFieldForRow() {
        return "id";
    }
}
