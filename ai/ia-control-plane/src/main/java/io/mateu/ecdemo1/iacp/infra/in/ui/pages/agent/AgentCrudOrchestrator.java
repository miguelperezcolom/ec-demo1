package io.mateu.ecdemo1.iacp.infra.in.ui.pages.agent;

import io.mateu.ecdemo1.iacp.application.out.query.AgentQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.dto.AgentDto;
import io.mateu.ecdemo1.iacp.application.out.query.dto.AgentRow;
import io.mateu.ecdemo1.iacp.application.usecases.agent.delete.DeleteAgentCommand;
import io.mateu.ecdemo1.iacp.application.usecases.agent.delete.DeleteAgentUseCase;
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
@Title("Agents")
public class AgentCrudOrchestrator extends CatalogueCrud<
        AgentViewModel, AgentViewModel, AgentViewModel, NoFilters, AgentRow, String, AgentDto> {

    final AgentViewModel viewModel;
    final DeleteAgentUseCase deleteAgentUseCase;
    final AgentQueryService queryService;

    @Override
    protected AgentViewModel editor() {
        return viewModel;
    }

    @Override
    protected AgentQueryService queries() {
        return queryService;
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        deleteAgentUseCase.handle(new DeleteAgentCommand(selectedIds));
    }
}
