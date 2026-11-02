package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.out.ContentQuery;
import io.mateu.ecdemo1.contenthub.application.usecases.UsageTracker;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Searchable;
import io.mateu.uidl.annotations.RowRoute;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 * Full-text search over what the reader may see: the filter is applied inside PostgreSQL with the
 * text, so pages and counts only ever count visible items. Words match by prefix ("vacac" finds
 * "vacaciones").
 */
@Service
@RowRoute("/intranet/noticias/${row.id}")
@Scope("prototype")
@Title("Buscar en la intranet")
public class SearchListing extends VisibleListing implements Searchable {

    final UsageTracker usage;

    public SearchListing(PortalSupport portal, ObjectProvider<ItemPage> pages, UsageTracker usage) {
        super(portal, pages);
        this.usage = usage;
    }

    @Override
    public ListingData<ItemRow> search(SearchRequest request, HttpRequest httpRequest) {
        var text = request == null ? null : request.searchText();
        if (text == null || text.isBlank()) {
            return portal.listing(httpRequest, request, ContentQuery.latest(20), "Escribe lo que buscas");
        }
        var found = portal.listing(httpRequest, request, ContentQuery.search(text, 0, 20),
                "Nada de lo que puedes ver coincide con «" + text + "»");
        // one search, not one per page of its results
        if (request.pageable() == null || request.pageable().page() <= 0) {
            usage.searched(portal.reader(httpRequest), text, found.page().totalElements());
        }
        return found;
    }
}
