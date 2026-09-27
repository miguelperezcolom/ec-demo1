package io.mateu.ecdemo1.mdm.ui.data;

import io.mateu.ecdemo1.mdm.resolution.IdentityResolution;
import io.mateu.ecdemo1.mdm.store.ChangeRequest;
import io.mateu.ecdemo1.mdm.store.ChangeRequestRepository;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.ui.Paging;
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
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
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

    final ChangeRequestRepository requests;
    final CustomerRepository customers;
    final IdentityResolution resolution;
    final ObjectProvider<CustomerCard> card;

    @Override
    public ListingData<ChangeRequestRow> search(SearchRequest request, HttpRequest httpRequest) {
        var text = request == null || request.searchText() == null ? "" : request.searchText().trim().toLowerCase(Locale.ROOT);
        var filters = request == null ? null : filters(request);
        var names = new HashMap<String, String>();
        var rows = requests.findAllByOrderByRequestedAtDesc().stream()
                .filter(r -> filters == null || filters.status == null || filters.status.isEmpty()
                        || filters.status.stream().anyMatch(s -> s.name().equals(r.status)))
                .map(r -> ChangeRequestRows.of(r, names.computeIfAbsent(r.customerId, this::nameOf)))
                .filter(row -> text.isEmpty() || (row.id() + " " + row.customer() + " " + row.origin() + " " + row.detail())
                        .toLowerCase(Locale.ROOT).contains(text))
                .toList();
        return Paging.page(rows, request);
    }

    String nameOf(String customerId) {
        return customers.findById(customerId).map(c -> c.fullName() + " · " + c.id).orElse(customerId);
    }

    @Override
    public CustomerCard view(String id, HttpRequest httpRequest) {
        var request = requests.findById(id).orElseThrow(() -> new NoSuchElementException("No change request " + id));
        return card.getObject().load(resolution.survivorOf(request.customerId));
    }
}
