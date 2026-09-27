package io.mateu.ecdemo1.iacp.infra.in.ui.pages.route;

import io.mateu.ecdemo1.iacp.application.out.query.RouteQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.dto.RouteDto;
import io.mateu.ecdemo1.iacp.application.out.query.dto.RouteRow;
import io.mateu.ecdemo1.iacp.application.usecases.route.delete.DeleteRouteCommand;
import io.mateu.ecdemo1.iacp.application.usecases.route.delete.DeleteRouteUseCase;
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
@Title("Routes")
public class RouteCrudOrchestrator extends CatalogueCrud<
        RouteViewModel, RouteViewModel, RouteViewModel, NoFilters, RouteRow, String, RouteDto> {

    final RouteViewModel viewModel;
    final DeleteRouteUseCase deleteRouteUseCase;
    final RouteQueryService queryService;

    @Override
    protected RouteViewModel editor() {
        return viewModel;
    }

    @Override
    protected RouteQueryService queries() {
        return queryService;
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        deleteRouteUseCase.handle(new DeleteRouteCommand(selectedIds));
    }
}
