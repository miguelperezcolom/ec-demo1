package io.mateu.ecdemo1.mapping.ui.pages;

import io.mateu.ecdemo1.mapping.ui.Paging;
import io.mateu.core.infra.declarative.orchestrators.crud.Crud;
import io.mateu.ecdemo1.mapping.store.EntryStatus;
import io.mateu.ecdemo1.mapping.store.MappingEntry;
import io.mateu.ecdemo1.mapping.store.MappingEntryRepository;
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

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * The dictionary, every version of every equivalence. Proposals first: they are what waits for
 * someone. An entry is never edited in place — saving one approves a new version of it.
 */
@Service
@RequiredArgsConstructor
@Scope("prototype")
@Title("Dictionary")
public class DictionaryCrud extends Crud<EntryViewModel, EntryViewModel, EntryViewModel, NoFilters, EntryRow, String> {

    final EntryViewModel viewModel;
    final MappingEntryRepository entries;

    @Override
    public ListingData<EntryRow> search(SearchRequest request, HttpRequest httpRequest) {
        var text = request.searchText() == null ? "" : request.searchText().toLowerCase();
        var rows = entries.findAll().stream()
                .filter(e -> (e.type + " " + e.sourceCode + " " + e.targetCode + " " + e.scope()).toLowerCase().contains(text))
                .sorted(Comparator.comparing((MappingEntry e) -> e.status == EntryStatus.PROPOSED ? 0 : e.status == EntryStatus.APPROVED ? 1 : 2)
                        .thenComparing(e -> e.type).thenComparing(e -> e.sourceCode))
                .map(DictionaryCrud::row).toList();
        return Paging.page(rows, request);
    }

    static EntryRow row(MappingEntry e) {
        return new EntryRow(e.id, e.type.name(), e.scope(), e.sourceCode, e.targetCode, e.entryVersion, status(e.status),
                e.proposedBy, e.decidedBy);
    }

    static Status status(EntryStatus status) {
        return switch (status) {
            case PROPOSED -> new Status(StatusType.WARNING, "Proposed");
            case APPROVED -> new Status(StatusType.SUCCESS, "Approved");
            case REJECTED -> new Status(StatusType.DANGER, "Rejected");
            case SUPERSEDED -> new Status(StatusType.NONE, "Superseded");
        };
    }

    @Override
    public EntryViewModel view(String id, HttpRequest httpRequest) {
        return viewModel.load(find(id));
    }

    @Override
    public EntryViewModel edit(String id, HttpRequest httpRequest) {
        return viewModel.load(find(id));
    }

    private MappingEntry find(String id) {
        return entries.findById(id).orElseThrow(() -> new NoSuchElementException("No mapping entry " + id));
    }

    @Override
    public EntryViewModel creationForm(HttpRequest httpRequest) {
        return viewModel;
    }

    @Override
    public String save(HttpRequest httpRequest) {
        return httpRequest.getComponentState(EntryViewModel.class).save(httpRequest);
    }

    @Override
    public String create(HttpRequest httpRequest) {
        return httpRequest.getComponentState(EntryViewModel.class).create(httpRequest);
    }

    @Override
    public void deleteAllById(List<String> selectedIds, HttpRequest httpRequest) {
        throw new IllegalStateException("The dictionary keeps its history: reject a proposal, or approve a new version");
    }

    @Override
    public String getIdFieldForRow() {
        return "id";
    }
}
