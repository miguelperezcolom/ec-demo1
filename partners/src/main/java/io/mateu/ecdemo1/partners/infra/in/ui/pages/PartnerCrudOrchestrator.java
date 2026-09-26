package io.mateu.ecdemo1.partners.infra.in.ui.pages;

import io.mateu.core.infra.declarative.orchestrators.crud.Crud;
import io.mateu.ecdemo1.partners.application.out.PartnerRepository;
import io.mateu.ecdemo1.partners.domain.partner.BillingMode;
import io.mateu.ecdemo1.partners.domain.partner.Partner;
import io.mateu.ecdemo1.partners.domain.partner.PartnerType;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.interfaces.HttpRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Scope("prototype")
@Title("Partners")
public class PartnerCrudOrchestrator extends Crud<PartnerViewModel, PartnerViewModel, PartnerViewModel, PartnerFilters,
        PartnerRow, String> {

    final PartnerViewModel viewModel;
    final PartnerRepository repository;

    @Override
    public ListingData<PartnerRow> search(SearchRequest request, HttpRequest httpRequest) {
        var filters = filters(request);
        var found = filters == null
                ? repository.search(request.searchText(), null, null, null,
                        request.pageable().page(), request.pageable().size())
                : repository.search(request.searchText(), filters.type, filters.billingMode,
                        filters.status == null ? null : filters.status == PartnerFilters.PartnerStatus.Active,
                        request.pageable().page(), request.pageable().size());
        var rows = found.partners().stream().map(PartnerCrudOrchestrator::row).toList();
        return new ListingData<>(new Page<>(request.searchText(), request.pageable().size(),
                request.pageable().page(), found.total(), rows));
    }

    static PartnerRow row(Partner p) {
        return new PartnerRow(p.getCode(), p.getDetails().name(), typeName(p.getDetails().type()),
                billingModeName(p.getDetails().billingMode()),
                p.isActive() ? new Status(StatusType.SUCCESS, "Active") : new Status(StatusType.NONE, "Inactive"),
                p.getVersion());
    }

    @Override
    public PartnerViewModel view(String id, HttpRequest httpRequest) {
        return viewModel.load(find(id));
    }

    @Override
    public PartnerViewModel edit(String id, HttpRequest httpRequest) {
        return viewModel.load(find(id));
    }

    private Partner find(String code) {
        return repository.findByCode(code).orElseThrow(() -> new NoSuchElementException("Partner not found: " + code));
    }

    @Override
    public PartnerViewModel creationForm(HttpRequest httpRequest) {
        return viewModel;
    }

    @Override
    public String save(HttpRequest httpRequest) {
        var editor = httpRequest.getComponentState(PartnerViewModel.class);
        editor.save(httpRequest);
        return editor.id();
    }

    @Override
    public String create(HttpRequest httpRequest) {
        return httpRequest.getComponentState(PartnerViewModel.class).create(httpRequest);
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        throw new IllegalStateException("A partner is never deleted from the master: deactivate it instead");
    }

    @Override
    public String getIdFieldForRow() {
        return "code";
    }

    /** How a person names each type of partner, rather than the constant. */
    static String typeName(PartnerType type) {
        return switch (type) {
            case TravelAgent -> "Travel agent";
            case TourOperator -> "Tour operator";
            case OnlineAgency -> "Online agency";
            case Company -> "Company";
        };
    }

    /** Who pays the stay, in words: the guest at the desk, or the partner on credit. */
    static String billingModeName(BillingMode mode) {
        return switch (mode) {
            case Front -> "Guest pays";
            case NoFront -> "Partner pays (credit)";
        };
    }
}
