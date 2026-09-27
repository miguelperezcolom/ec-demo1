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

/**
 * Who is told about what, for which hotel, and where: the only rule there is. A notification reaches
 * every active recipient that wants it, once per person, browser, address and space.
 */
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
                .map(r -> new RecipientRow(r.id, r.name, who(r), what(r),
                        r.hotelCode == null || r.hotelCode.isBlank() ? "any" : r.hotelCode, channels(r), r.active))
                .toList();
        return Paging.page(rows, request);
    }

    /** Its users, its roles (as role:…) and its address. */
    static String who(io.mateu.ecdemo1.communication.store.Recipient r) {
        var who = new java.util.ArrayList<String>(r.userList());
        r.roleList().forEach(role -> who.add("role:" + role));
        if (r.email != null && !r.email.isBlank()) {
            who.add(r.email);
        }
        return String.join(", ", who);
    }

    /** Its types, "all" when it names none, and the forms engine's tasks when it wants them. */
    static String what(io.mateu.ecdemo1.communication.store.Recipient r) {
        var types = r.typeList();
        return (types.isEmpty() ? "all" : String.join(", ", types)) + (r.tasks ? " + tasks" : "");
    }

    /** Its channels, the spaces of Google Chat in brackets when it names some. */
    static String channels(io.mateu.ecdemo1.communication.store.Recipient r) {
        return r.channelSet().stream().map(c -> c == io.mateu.ecdemo1.communication.store.Channel.GOOGLE_CHAT
                        && r.chatSpaces != null && !r.chatSpaces.isBlank() ? c.name() + " (" + r.chatSpaces + ")" : c.name())
                .reduce((a, b) -> a + ", " + b).orElse("");
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
