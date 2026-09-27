package io.mateu.ecdemo1.mdm.ui.data;

import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.mdm.resolution.IdentityResolution;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.application.CustomerQueries;
import io.mateu.ecdemo1.mdm.application.CustomerSearch;
import io.mateu.ecdemo1.uicommons.paging.DbPaging;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Pageable;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.Filterable;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import io.mateu.uidl.interfaces.Navigable;
import io.mateu.uidl.interfaces.Searchable;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Finding a customer: by name, email, phone or document — the free text looks in all of them and in
 * the code. Merged customers are not listed: their code opens the customer they became part of.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Clientes")
public class CustomerSearchPage implements Listing<CustomerSearchRow>, Searchable, Filterable<CustomerFilters>,
        Navigable<CustomerCard, String> {

    static final int PAGE_SIZE = 20;

    /** Grid column → customer property: what a click on a column's header sorts by. */
    static final Map<String, String> SORTABLE = Map.of("id", "id", "email", "email", "phone", "phone",
            "document", "documentNumber", "status", "status");

    final CustomerQueries customers;
    final IdentityResolution resolution;
    final ObjectProvider<CustomerCard> card;

    @Override
    public ListingData<CustomerSearchRow> search(SearchRequest request, HttpRequest httpRequest) {
        var pageable = request == null ? null : request.pageable();
        var sized = pageable == null || pageable.size() <= 0
                ? new Pageable(pageable == null ? 0 : pageable.page(), PAGE_SIZE, pageable == null ? List.of() : pageable.sort())
                : pageable;
        var text = request == null ? null : request.searchText();
        var found = customers.search(search(text, request == null ? null : filters(request)),
                DbPaging.pageable(sized, SORTABLE));
        var listing = DbPaging.listing(text == null ? "" : text, found, this::row);
        return new ListingData<>(listing.page(), "Ningún cliente coincide");
    }

    CustomerSearchRow row(Customer c) {
        return new CustomerSearchRow(c.id, c.fullName(), nullToEmpty(c.email), nullToEmpty(c.phone),
                c.documentNumber == null ? "" : (c.documentType == null ? "" : c.documentType + " ") + c.documentNumber,
                customers.reservationsOf(c.id), Estados.customer(c.status));
    }

    /** The screen's free text and filters, as the customers' search. */
    static CustomerSearch search(String text, CustomerFilters filters) {
        if (filters == null) {
            return CustomerSearch.text(text);
        }
        return new CustomerSearch(text, filters.name, filters.email, filters.phone, filters.document,
                filters.status == null ? null : filters.status.stream()
                        .map(s -> CustomerStatus.valueOf(s.name())).collect(Collectors.toSet()));
    }

    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Any code the customer ever had opens it: a merged one opens the customer it became part of. */
    @Override
    public CustomerCard view(String id, HttpRequest httpRequest) {
        return card.getObject().load(resolution.survivorOf(id));
    }
}
