package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
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

import java.util.Comparator;
import java.util.Locale;

/**
 * The pages, to edit one on the page itself ({@link VisualEditorPage}, /editorial/visual/{id}). An item's
 * form opens the same route with «Editar sobre la página».
 */
@Service
@Scope("prototype")
@Title("Editor visual")
@RowRoute("/editorial/visual/${row.id}")
@Editors
public class VisualEditorListing implements Listing<PageRow>, Navigable<VisualEditorPage, String>, Searchable {

    final ItemRepository items;
    final ObjectProvider<VisualEditorPage> pages;

    public VisualEditorListing(ItemRepository items, ObjectProvider<VisualEditorPage> pages) {
        this.items = items;
        this.pages = pages;
    }

    @Override
    public ListingData<PageRow> search(SearchRequest request, HttpRequest httpRequest) {
        var text = request == null || request.searchText() == null ? "" : request.searchText().trim().toLowerCase(Locale.ROOT);
        var rows = items.findAll().stream()
                .filter(i -> i.type() == ItemType.PAGE)
                .filter(i -> text.isEmpty() || title(i).toLowerCase(Locale.ROOT).contains(text) || i.id().contains(text))
                .sorted(Comparator.comparing(Item::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(i -> new PageRow(i.id(), title(i), ItemForm.status(i), i.draft().blocks().size(), Ui.when(i.updatedAt())))
                .toList();
        return new ListingData<>(new Page<>(text, Math.max(rows.size(), 1), 0, rows.size(), rows),
                "No hay páginas: crea un contenido de tipo «Página» en Contenidos");
    }

    static String title(Item i) {
        return i.draft().in(i.defaultLanguage(), i.defaultLanguage()).title();
    }

    @Override
    public VisualEditorPage view(String id, HttpRequest httpRequest) {
        return pages.getObject().load(id);
    }
}
