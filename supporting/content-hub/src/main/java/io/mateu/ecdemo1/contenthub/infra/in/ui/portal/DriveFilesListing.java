package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.drive.DriveDocuments;
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
 * «Documentos de Drive»: the Drive files the items the reader sees point at, each once. A row opens the item,
 * with the file's live preview and the ways to Drive.
 */
@Service
@RowRoute("/intranet/noticias/${row.id}")
@Scope("prototype")
@Title("Documentos de Drive")
public class DriveFilesListing implements Listing<DriveFilesRow>, Navigable<ItemPage, String>, Searchable {

    final PortalSupport portal;
    final DriveDocuments drive;
    final ObjectProvider<ItemPage> pages;

    public DriveFilesListing(PortalSupport portal, DriveDocuments drive, ObjectProvider<ItemPage> pages) {
        this.portal = portal;
        this.drive = drive;
        this.pages = pages;
    }

    @Override
    public ListingData<DriveFilesRow> search(SearchRequest request, HttpRequest httpRequest) {
        var reader = portal.reader(httpRequest);
        var text = request == null || request.searchText() == null ? "" : request.searchText().trim();
        var words = People.fold(text);
        var rows = drive.visibleTo(reader.attributes()).stream()
                .filter(d -> People.fold(d.name() + " " + d.itemTitle()).contains(words))
                .map(d -> new DriveFilesRow(d.itemId(), d.name(), d.ref().type().label(),
                        PortalComponents.driveModified(d), d.itemTitle()))
                .toList();
        var pageable = request == null ? null : request.pageable();
        var size = pageable == null || pageable.size() <= 0 ? 20 : pageable.size();
        var number = pageable == null ? 0 : Math.max(0, pageable.page());
        return new ListingData<>(new Page<>(text, size, number, rows.size(),
                rows.stream().skip((long) number * size).limit(size).toList()),
                text.isEmpty() ? "Ningún contenido que puedes ver enlaza a Drive" : "Nada coincide con «" + text + "»");
    }

    /** Not reached from a row (@RowRoute opens the item's own route), but what makes it a crud. */
    @Override
    public ItemPage view(String id, HttpRequest httpRequest) {
        return pages.getObject().load(id, httpRequest);
    }
}
