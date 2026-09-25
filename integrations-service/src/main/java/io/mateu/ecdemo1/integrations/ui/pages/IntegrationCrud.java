package io.mateu.ecdemo1.integrations.ui.pages;

import io.mateu.ecdemo1.integrations.ui.Paging;
import io.mateu.core.infra.declarative.orchestrators.crud.Crud;
import io.mateu.ecdemo1.integration.model.integration.IntegrationStatus;
import io.mateu.ecdemo1.integrations.store.BackfillRunRepository;
import io.mateu.ecdemo1.integrations.store.Integration;
import io.mateu.ecdemo1.integrations.store.IntegrationRepository;
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
import org.springframework.stereotype.Service;

import java.util.List;
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

    final IntegrationViewModel viewModel;
    final IntegrationRepository integrations;
    final BackfillRunRepository runs;

    @Override
    public ListingData<IntegrationRow> search(SearchRequest request, HttpRequest httpRequest) {
        var text = request.searchText() == null ? "" : request.searchText().toLowerCase();
        var rows = integrations.findAllByOrderByCrsHotelCodeAsc().stream()
                .filter(i -> (i.crsHotelCode + " " + i.pmsHotelCode + " " + i.name + " " + i.status).toLowerCase().contains(text))
                .map(this::row).toList();
        return Paging.page(rows, request);
    }

    IntegrationRow row(Integration i) {
        var run = runs.findFirstByIntegrationIdOrderByStartedAtDesc(i.id).orElse(null);
        return new IntegrationRow(i.crsHotelCode, i.pmsHotelCode, i.name, status(i.status),
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
        return integrations.findByCrsHotelCode(crsHotelCode)
                .orElseThrow(() -> new NoSuchElementException("No integration for hotel " + crsHotelCode));
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
