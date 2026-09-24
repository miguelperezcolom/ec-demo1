package io.mateu.ecdemo1.communication.ui.pages;

import io.mateu.ecdemo1.communication.ui.Paging;
import io.mateu.core.infra.declarative.orchestrators.crud.Crud;
import io.mateu.ecdemo1.communication.store.RecipientRepository;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.NoFilters;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

/** Who is told about what, and for which hotel. */
@Service
@RequiredArgsConstructor
@Scope("prototype")
@Title("Recipients")
public class RecipientsCrud extends Crud<RecipientViewModel, RecipientViewModel, RecipientViewModel, NoFilters, RecipientRow, String> {

    final RecipientViewModel viewModel;
    final RecipientRepository recipients;

    @Override
    public ListingData<RecipientRow> search(SearchRequest request, HttpRequest httpRequest) {
        var rows = recipients.findAll().stream()
                .map(r -> new RecipientRow(r.id, r.name, r.email, r.notificationType == null ? "any" : r.notificationType.name(),
                        r.hotelCode == null || r.hotelCode.isBlank() ? "any" : r.hotelCode, r.active))
                .toList();
        return Paging.page(rows, request);
    }

    @Override
    public RecipientViewModel view(String id, HttpRequest httpRequest) {
        return viewModel.load(recipients.findById(id).orElseThrow(() -> new NoSuchElementException("No recipient " + id)));
    }

    @Override
    public RecipientViewModel edit(String id, HttpRequest httpRequest) {
        return view(id, httpRequest);
    }

    @Override
    public RecipientViewModel creationForm(HttpRequest httpRequest) {
        return viewModel;
    }

    @Override
    public String save(HttpRequest httpRequest) {
        return httpRequest.getComponentState(RecipientViewModel.class).save();
    }

    @Override
    public String create(HttpRequest httpRequest) {
        return httpRequest.getComponentState(RecipientViewModel.class).save();
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        recipients.deleteAllById(selectedIds);
    }

    @Override
    public String getIdFieldForRow() {
        return "id";
    }
}
