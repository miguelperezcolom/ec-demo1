package io.mateu.ecdemo1.partners.infra.in.ui.pages;

import io.mateu.core.infra.declarative.orchestrators.crud.Crud;
import io.mateu.ecdemo1.partners.application.out.PartnerRepository;
import io.mateu.ecdemo1.partners.domain.partner.Partner;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.NoFilters;
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
public class PartnerCrudOrchestrator extends Crud<PartnerViewModel, PartnerViewModel, PartnerViewModel, NoFilters,
        PartnerRow, String> {

    final PartnerViewModel viewModel;
    final PartnerRepository repository;

    @Override
    public ListingData<PartnerRow> search(SearchRequest request, HttpRequest httpRequest) {
        var rows = repository.search(request.searchText(), request.pageable().page(), request.pageable().size())
                .stream().map(PartnerCrudOrchestrator::row).toList();
        return new ListingData<>(new Page<>(request.searchText(), request.pageable().size(),
                request.pageable().page(), repository.count(), rows));
    }

    static PartnerRow row(Partner p) {
        return new PartnerRow(p.getCode(), p.getDetails().name(), p.getDetails().type().name(),
                p.getDetails().billingMode().name(),
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
}
