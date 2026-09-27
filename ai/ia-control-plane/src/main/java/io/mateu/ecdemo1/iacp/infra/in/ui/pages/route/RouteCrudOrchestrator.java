package io.mateu.ecdemo1.iacp.infra.in.ui.pages.route;

import io.mateu.core.infra.declarative.orchestrators.crud.Crud;
import io.mateu.ecdemo1.iacp.application.out.query.RouteQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.dto.RouteRow;
import io.mateu.ecdemo1.iacp.application.usecases.route.delete.DeleteRouteCommand;
import io.mateu.ecdemo1.iacp.application.usecases.route.delete.DeleteRouteUseCase;
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
@Title("Routes")
public class RouteCrudOrchestrator extends Crud<
        RouteViewModel, RouteViewModel, RouteViewModel, NoFilters, RouteRow, String> {

    final RouteViewModel viewModel;
    final DeleteRouteUseCase deleteRouteUseCase;
    final RouteQueryService queryService;

    @Override
    public ListingData<RouteRow> search(SearchRequest request, HttpRequest httpRequest) {
        return queryService.findAll(request.searchText(), filters(request), request.pageable());
    }

    @Override
    public RouteViewModel view(String id, HttpRequest httpRequest) {
        return viewModel.load(queryService.getById(id).orElseThrow());
    }

    @Override
    public RouteViewModel edit(String id, HttpRequest httpRequest) {
        return viewModel.load(queryService.getById(id).orElseThrow());
    }

    @Override
    public RouteViewModel creationForm(HttpRequest httpRequest) {
        return viewModel;
    }

    @Override
    public String save(HttpRequest httpRequest) {
        var editor = httpRequest.getComponentState(RouteViewModel.class);
        editor.save(httpRequest);
        return editor.id();
    }

    @Override
    public String create(HttpRequest httpRequest) {
        return httpRequest.getComponentState(RouteViewModel.class).create(httpRequest);
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        deleteRouteUseCase.handle(new DeleteRouteCommand(selectedIds));
    }

    @Override
    public String getIdFieldForRow() {
        return "id";
    }
}
