package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.PreviewLinks;
import io.mateu.ecdemo1.contenthub.domain.preview.PreviewLink;
import io.mateu.ecdemo1.contenthub.domain.preview.PreviewLinkStatus;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.RowRoute;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import io.mateu.uidl.interfaces.Navigable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * The contents with reviewer preview links, each with how many still work and how often they were opened;
 * a row opens the item's links ({@link PreviewLinksPage}), as «Enlace de vista previa» in its form does.
 */
@Service
@Scope("prototype")
@Title("Enlaces de vista previa")
@RowRoute("/editorial/vistaPrevia/${row.id}")
@Editors
public class PreviewLinksListing implements Listing<PreviewLinksListing.Row>, Navigable<PreviewLinksPage, String> {

    public record Row(@Label("Id") String id, @Label("Contenido") String title, @Label("Activos") long active,
                      @Label("Enlaces") long links, @Label("Vistas") long views, @Label("Último creado") String latest) {
    }

    final PreviewLinks previewLinks;
    final ItemRepository items;
    final ObjectProvider<PreviewLinksPage> pages;

    public PreviewLinksListing(PreviewLinks previewLinks, ItemRepository items, ObjectProvider<PreviewLinksPage> pages) {
        this.previewLinks = previewLinks;
        this.items = items;
        this.pages = pages;
    }

    @Override
    public ListingData<Row> search(SearchRequest request, HttpRequest httpRequest) {
        var byItem = new LinkedHashMap<String, List<PreviewLink>>();
        previewLinks.all().forEach(l -> byItem.computeIfAbsent(l.itemId(), k -> new java.util.ArrayList<>()).add(l));
        var rows = byItem.entrySet().stream().map(e -> {
            var item = items.find(e.getKey()).orElse(null);
            if (item == null) {
                return null;
            }
            var links = e.getValue();
            return new Row(e.getKey(), item.draft().in(item.defaultLanguage(), item.defaultLanguage()).title(),
                    links.stream().filter(l -> previewLinks.status(l) == PreviewLinkStatus.ACTIVE).count(), links.size(),
                    links.stream().mapToLong(PreviewLink::viewCount).sum(),
                    Ui.when(links.stream().map(PreviewLink::createdAt).max(Comparator.naturalOrder()).orElse(null)));
        }).filter(Objects::nonNull).toList();
        return new ListingData<>(new Page<>("", Math.max(rows.size(), 1), 0, rows.size(), rows),
                "Sin enlaces de vista previa: créalos desde el formulario de un contenido, con «Enlace de vista previa»");
    }

    @Override
    public PreviewLinksPage view(String id, HttpRequest httpRequest) {
        return pages.getObject().load(id);
    }
}
