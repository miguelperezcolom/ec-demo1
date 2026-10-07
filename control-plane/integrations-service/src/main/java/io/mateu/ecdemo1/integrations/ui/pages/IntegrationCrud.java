package io.mateu.ecdemo1.integrations.ui.pages;

import io.mateu.ecdemo1.integrations.application.IntegrationQueries;
import io.mateu.ecdemo1.uicommons.paging.DbPaging;
import io.mateu.core.infra.declarative.orchestrators.crud.Crud;
import io.mateu.ecdemo1.integration.model.integration.IntegrationStatus;
import io.mateu.ecdemo1.integrations.store.Integration;
import io.mateu.ecdemo1.integrations.rest.IntegrationDto;
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
 * One integration per hotel. Creating one registers it and starts its onboarding; the rest of its
 * life is the toolbar of its detail. It is never deleted: it is decommissioned, and stays as record.
 */
@Service
@RequiredArgsConstructor
@Scope("prototype")
@Title("Integrations")
public class IntegrationCrud extends Crud<IntegrationViewModel, IntegrationViewModel, IntegrationViewModel, NoFilters,
        IntegrationRow, String> {

    /** Grid column → integration property: what a click on a column's header sorts by. */
    static final Map<String, String> SORTABLE = Map.of("crsHotel", "crsHotelCode", "operaProperty", "pmsHotelCode",
            "name", "name", "status", "status");

    final IntegrationViewModel viewModel;
    final IntegrationQueries queries;

    @Override
    public ListingData<IntegrationRow> search(SearchRequest request, HttpRequest httpRequest) {
        return DbPaging.page(request, p -> queries.find(request.searchText(), PageRequest.of(p.getPageNumber(),
                p.getPageSize(), DbPaging.pageable(request.pageable(), SORTABLE).getSort())), this::row);
    }

    IntegrationRow row(Integration i) {
        var run = queries.lastBackfill(i.id).orElse(null);
        return new IntegrationRow(i.crsHotelCode, i.pmsHotelCode, i.name, status(i.getStatus()),
                IntegrationDto.waitingFor(i.gate),
                run == null ? "" : "%s · %d%s".formatted(run.status, run.dispatched, run.expected == null ? "" : "/" + run.expected));
    }

    static Status status(IntegrationStatus status) {
        return new Status(switch (status) {
            case ACTIVE -> StatusType.SUCCESS;
            case READY_TO_ACTIVATE, BACKFILLING, SYNCING_PARTNERS, CREATED -> StatusType.INFO;
            case CONNECTIVITY_FAILED, PENDING_CONFIGURATION, BACKFILL_BLOCKED, MAPPING_PENDING, PAUSED -> StatusType.WARNING;
            case DECOMMISSIONED -> StatusType.NONE;
        }, status.name());
    }

    @Override
    public IntegrationViewModel view(String id, HttpRequest httpRequest) {
        return viewModel.load(find(id));
    }

    @Override
    public IntegrationViewModel edit(String id, HttpRequest httpRequest) {
        return viewModel.load(find(id));
    }

    /** By the CRS hotel: it is the row's id, and what the URL of a screen carries. */
    private Integration find(String crsHotelCode) {
        return queries.byCrsHotel(crsHotelCode)
                .orElseThrow(() -> new NoSuchElementException("Integration for hotel " + crsHotelCode + " not found"));
    }

    @Override
    public IntegrationViewModel creationForm(HttpRequest httpRequest) {
        return viewModel.blank();
    }

    @Override
    public String save(HttpRequest httpRequest) {
        return httpRequest.getComponentState(IntegrationViewModel.class).save(httpRequest);
    }

    @Override
    public String create(HttpRequest httpRequest) {
        return httpRequest.getComponentState(IntegrationViewModel.class).create(httpRequest);
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        throw new IllegalStateException("An integration is not deleted: decommission it, and it stays as record");
    }

    @Override
    public String getIdFieldForRow() {
        return "crsHotel";
    }
}
