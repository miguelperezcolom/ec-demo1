package io.mateu.ecdemo1.iacp.infra.in.ui.pages.mcp;

import io.mateu.ecdemo1.iacp.application.out.query.McpQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.dto.McpDto;
import io.mateu.ecdemo1.iacp.application.out.query.dto.McpRow;
import io.mateu.ecdemo1.iacp.application.usecases.mcp.delete.DeleteMcpCommand;
import io.mateu.ecdemo1.iacp.application.usecases.mcp.delete.DeleteMcpUseCase;
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
@Title("MCP servers")
public class McpCrudOrchestrator extends CatalogueCrud<
        McpViewModel, McpViewModel, McpViewModel, NoFilters, McpRow, String, McpDto> {

    final McpViewModel viewModel;
    final DeleteMcpUseCase deleteMcpUseCase;
    final McpQueryService queryService;

    @Override
    protected McpViewModel editor() {
        return viewModel;
    }

    @Override
    protected McpQueryService queries() {
        return queryService;
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        deleteMcpUseCase.handle(new DeleteMcpCommand(selectedIds));
    }
}
