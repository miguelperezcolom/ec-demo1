package io.mateu.ecdemo1.integrations.ui.pages;

import io.mateu.core.infra.declarative.orchestrators.crud.Crud;
import io.mateu.ecdemo1.integrations.application.FrontOfficeIntegrationQueries;
import io.mateu.ecdemo1.integrations.store.FoIntegrationStatus;
import io.mateu.ecdemo1.integrations.store.FrontOfficeIntegration;
import io.mateu.ecdemo1.integrations.rest.FrontOfficeIntegrationDto;
import io.mateu.ecdemo1.uicommons.paging.DbPaging;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.NoFilters;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.interfaces.HttpRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * The front offices fed from a PMS (pms-fo integrations), one per PMS property. Creating one
 * registers it and starts its onboarding; the rest of its life is the toolbar of its detail. It is
 * never deleted: it is decommissioned, and stays as record.
 */
@Service
@RequiredArgsConstructor
@Scope("prototype")
@Title("PMS → Front office")
public class FrontOfficeIntegrationCrud extends Crud<FrontOfficeIntegrationViewModel, FrontOfficeIntegrationViewModel,
        FrontOfficeIntegrationViewModel, NoFilters, FrontOfficeIntegrationRow, String> {

    static final Map<String, String> SORTABLE = Map.of("property", "pmsHotelCode", "frontOffice", "frontOfficeCode",
            "name", "name", "status", "status");

    final FrontOfficeIntegrationViewModel viewModel;
    final FrontOfficeIntegrationQueries queries;

    @Override
    public ListingData<FrontOfficeIntegrationRow> search(SearchRequest request, HttpRequest httpRequest) {
        return DbPaging.page(request, p -> queries.find(request.searchText(), PageRequest.of(p.getPageNumber(),
                p.getPageSize(), DbPaging.pageable(request.pageable(), SORTABLE).getSort())), this::row);
    }

    FrontOfficeIntegrationRow row(FrontOfficeIntegration i) {
        var run = queries.lastBackfill(i.id).orElse(null);
        return new FrontOfficeIntegrationRow(i.pmsHotelCode, i.frontOfficeCode, i.name, status(i.getStatus()),
                FrontOfficeIntegrationDto.waitingFor(i.gate),
                run == null ? "" : "%s · %d%s".formatted(run.status, run.dispatched, run.expected == null ? "" : "/" + run.expected),
                i.lastPollAt == null ? "" : "%s · %d change(s)".formatted(i.lastPollAt,
                        i.lastPollChanges == null ? 0 : i.lastPollChanges));
    }

    static Status status(FoIntegrationStatus status) {
        return new Status(switch (status) {
            case ACTIVE -> StatusType.SUCCESS;
            case READY_TO_ACTIVATE, BACKFILLING, SYNCING_CATALOGUE, CREATED -> StatusType.INFO;
            case CONNECTIVITY_FAILED, PAUSED -> StatusType.WARNING;
            case DECOMMISSIONED -> StatusType.NONE;
        }, status.name());
    }

    @Override
    public FrontOfficeIntegrationViewModel view(String id, HttpRequest httpRequest) {
        return viewModel.load(find(id));
    }

    @Override
    public FrontOfficeIntegrationViewModel edit(String id, HttpRequest httpRequest) {
        return viewModel.load(find(id));
    }

    private FrontOfficeIntegration find(String pmsHotelCode) {
        return queries.byProperty(pmsHotelCode)
                .orElseThrow(() -> new NoSuchElementException("Front office integration for property " + pmsHotelCode + " not found"));
    }

    @Override
    public FrontOfficeIntegrationViewModel creationForm(HttpRequest httpRequest) {
        return viewModel.blank();
    }

    @Override
    public String save(HttpRequest httpRequest) {
        return httpRequest.getComponentState(FrontOfficeIntegrationViewModel.class).save(httpRequest);
    }

    @Override
    public String create(HttpRequest httpRequest) {
        return httpRequest.getComponentState(FrontOfficeIntegrationViewModel.class).create(httpRequest);
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        throw new IllegalStateException("An integration is not deleted: decommission it, and it stays as record");
    }

    @Override
    public String getIdFieldForRow() {
        return "property";
    }
}
