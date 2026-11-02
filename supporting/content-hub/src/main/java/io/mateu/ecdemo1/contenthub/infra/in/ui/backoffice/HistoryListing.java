package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.uidl.annotations.RowRoute;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import io.mateu.uidl.interfaces.Navigable;
import io.mateu.uidl.interfaces.Searchable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 * The items, to open one's history ({@link HistoryPage}, /editorial/historial/{id}). An item's form opens
 * the same route with «Historial».
 */
@Service
@Scope("prototype")
@Title("Historial de versiones")
@RowRoute("/editorial/historial/${row.id}")
@Editors
public class HistoryListing implements Listing<ItemRow>, Navigable<HistoryPage, String>, Searchable {

    final ItemRepository items;
    final ObjectProvider<HistoryPage> pages;

    public HistoryListing(ItemRepository items, ObjectProvider<HistoryPage> pages) {
        this.items = items;
        this.pages = pages;
    }

    @Override
    public ListingData<ItemRow> search(SearchRequest request, HttpRequest httpRequest) {
        var text = request == null ? "" : request.searchText();
        var pageable = request == null ? null : request.pageable();
        var size = pageable == null || pageable.size() <= 0 ? 20 : pageable.size();
        var number = pageable == null ? 0 : Math.max(0, pageable.page());
        var rows = items.search(text, number * size, size).stream()
                .map(s -> new ItemRow(s.id(), s.title(), ItemType.valueOf(s.type()).label(), ItemsCrud.status(s), s.author(),
                        s.updatedAt()))
                .toList();
        return new ListingData<>(new Page<>(text == null ? "" : text, size, number, items.count(text), rows),
                "Todavía no hay contenidos");
    }

    @Override
    public HistoryPage view(String id, HttpRequest httpRequest) {
        return pages.getObject().load(id, httpRequest);
    }
}
