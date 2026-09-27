package io.mateu.ecdemo1.iacp.infra.in.ui.pages.apimcp;

import io.mateu.ecdemo1.iacp.application.out.query.ApiMcpQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.dto.ApiMcpDto;
import io.mateu.ecdemo1.iacp.application.out.query.dto.ApiMcpRow;
import io.mateu.ecdemo1.iacp.application.usecases.apimcp.delete.DeleteApiMcpCommand;
import io.mateu.ecdemo1.iacp.application.usecases.apimcp.delete.DeleteApiMcpUseCase;
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
@Title("APIs as MCP servers")
public class ApiMcpCrudOrchestrator extends CatalogueCrud<
        ApiMcpViewModel, ApiMcpViewModel, ApiMcpViewModel, NoFilters, ApiMcpRow, String, ApiMcpDto> {

    final ApiMcpViewModel viewModel;
    final DeleteApiMcpUseCase deleteApiMcpUseCase;
    final ApiMcpQueryService queryService;

    @Override
    protected ApiMcpViewModel editor() {
        return viewModel;
    }

    @Override
    protected ApiMcpQueryService queries() {
        return queryService;
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        deleteApiMcpUseCase.handle(new DeleteApiMcpCommand(selectedIds));
    }
}
