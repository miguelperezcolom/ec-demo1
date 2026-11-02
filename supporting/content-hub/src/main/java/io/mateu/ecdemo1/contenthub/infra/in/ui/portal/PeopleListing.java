package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import io.mateu.uidl.annotations.RowRoute;
import io.mateu.uidl.interfaces.Searchable;
import io.mateu.uidl.interfaces.Navigable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 * The people of the company, by name, job, department, hotel or region ("recepción tenerife"). Each
 * row opens the person's card.
 */
@RowRoute("/intranet/personas/${row.id}")
@Service
@Scope("prototype")
@Title("Personas")
public class PeopleListing implements Listing<PersonRow>, Navigable<PersonPage, String>, Searchable {

    final PortalSupport portal;
    final ObjectProvider<PersonPage> pages;
    final People people;

    public PeopleListing(PortalSupport portal, People people, ObjectProvider<PersonPage> pages) {
        this.pages = pages;
        this.portal = portal;
        this.people = people;
    }

    @Override
    public ListingData<PersonRow> search(SearchRequest request, HttpRequest httpRequest) {
        portal.reader(httpRequest);
        var text = request == null || request.searchText() == null ? "" : request.searchText().trim();
        var pageable = request == null ? null : request.pageable();
        var size = pageable == null || pageable.size() <= 0 ? 20 : pageable.size();
        var number = pageable == null ? 0 : Math.max(0, pageable.page());
        var found = people.search(text);
        var rows = found.stream().skip((long) number * size).limit(size)
                .map(e -> new PersonRow(e.userId(), People.name(e), people.jobTitle(e), people.department(e),
                        people.hotel(e).isEmpty() ? "Servicios centrales" : people.hotel(e),
                        e.phone() == null ? "" : e.phone(), e.email() == null ? "" : e.email()))
                .toList();
        return new ListingData<>(new Page<>(text, size, number, found.size(), rows),
                text.isEmpty() ? "El directorio está vacío" : "Nadie coincide con «" + text + "»");
    }


    /** Not reached from a row (@RowRoute opens the page's own route), but what makes it a crud. */
    @Override
    public PersonPage view(String id, HttpRequest httpRequest) {
        return pages.getObject().load(id, httpRequest);
    }
}
