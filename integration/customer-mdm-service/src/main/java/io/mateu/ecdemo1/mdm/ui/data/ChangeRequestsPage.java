package io.mateu.ecdemo1.mdm.ui.data;

import io.mateu.ecdemo1.mdm.resolution.IdentityResolution;
import io.mateu.ecdemo1.mdm.store.ChangeRequest;
import io.mateu.ecdemo1.mdm.application.ChangeRequestQueries;
import io.mateu.ecdemo1.mdm.application.CustomerQueries;
import io.mateu.ecdemo1.uicommons.paging.DbPaging;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.Filterable;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import io.mateu.uidl.interfaces.Navigable;
import io.mateu.uidl.interfaces.Searchable;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * The changes hotels asked for, newest first, and how Salesforce decided them. Opening one opens its
 * customer.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Solicitudes de cambio")
public class ChangeRequestsPage implements Listing<ChangeRequestRow>, Searchable,
        Filterable<ChangeRequestsPage.Filters>, Navigable<CustomerCard, String> {

    public static class Filters {
        @Label("Estado")
        Set<ChangeRequest.Status> status;
    }

    /** Grid column → change request property: what a click on a column's header sorts by. */
    static final Map<String, String> SORTABLE = Map.of("id", "id", "requestedAt", "requestedAt", "origin", "origin",
            "status", "status", "decidedAt", "decidedAt", "salesforceCase", "salesforceCaseId");

    final ChangeRequestQueries requests;
    final CustomerQueries customers;
    final IdentityResolution resolution;
    final ObjectProvider<CustomerCard> card;

    @Override
    public ListingData<ChangeRequestRow> search(SearchRequest request, HttpRequest httpRequest) {
        var filters = request == null ? null : filters(request);
        var text = request == null ? null : request.searchText();
        var names = new HashMap<String, String>();
        return DbPaging.page(request, p -> requests.find(text, filters == null ? null : filters.status,
                        PageRequest.of(p.getPageNumber(), p.getPageSize(),
                                DbPaging.pageable(request == null ? null : request.pageable(), SORTABLE).getSort())),
                r -> ChangeRequestRows.of(r, names.computeIfAbsent(r.customerId, customers::label)));
    }

    @Override
    public CustomerCard view(String id, HttpRequest httpRequest) {
        var request = requests.find(id).orElseThrow(() -> new NoSuchElementException("No change request " + id));
        return card.getObject().load(resolution.survivorOf(request.customerId));
    }
}
