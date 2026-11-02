package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.Collaboration;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import io.mateu.uidl.interfaces.Navigable;
import io.mateu.uidl.interfaces.Searchable;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;

/** Every comment on every item, the hidden ones too, newest first: opening one moderates it. */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Comentarios")
@Editors
public class CommentsListing implements Listing<CommentRow>, Navigable<CommentPage, String>, Searchable {

    final Collaboration collaboration;
    final ItemRepository items;
    final Readers readers;
    final ObjectProvider<CommentPage> pages;

    @Override
    public ListingData<CommentRow> search(SearchRequest request, HttpRequest httpRequest) {
        var text = request == null || request.searchText() == null ? "" : request.searchText().trim().toLowerCase(Locale.ROOT);
        var titles = new HashMap<String, String>();
        var rows = collaboration.allComments(readers.of(httpRequest)).stream().map(m -> {
            var c = m.comment();
            return new CommentRow(m.itemId() + "~" + c.id(), c.text(),
                    titles.computeIfAbsent(m.itemId(), id -> ItemTitles.of(items, id)),
                    c.authorName() == null || c.authorName().isBlank() ? c.authorId() : c.authorName(), Ui.when(c.at()),
                    c.hidden() ? new Status(StatusType.DANGER, "Oculto") : new Status(StatusType.SUCCESS, "Visible"));
        }).filter(r -> text.isEmpty() || (r.text() + " " + r.item() + " " + r.author()).toLowerCase(Locale.ROOT).contains(text))
                .toList();
        return new ListingData<>(new Page<>(text, Math.max(20, rows.size()), 0, rows.size(), rows),
                "Todavía no hay comentarios");
    }

    @Override
    public CommentPage view(String id, HttpRequest httpRequest) {
        return pages.getObject().load(id, httpRequest);
    }
}
