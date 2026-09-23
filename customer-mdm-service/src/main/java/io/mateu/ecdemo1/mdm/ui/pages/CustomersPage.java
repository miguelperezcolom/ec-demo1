package io.mateu.ecdemo1.mdm.ui.pages;

import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.mdm.resolution.IdentityResolution;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.store.SourceRepository;
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

import java.util.Locale;

/**
 * The golden records: who the customers are, as the MDM holds them. Absorbed ones are not listed —
 * they are aliases, and their code opens the survivor.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Golden records")
public class CustomersPage implements Listing<CustomerRow>, Searchable, Navigable<CustomerViewModel, String> {

    final CustomerRepository customers;
    final SourceRepository sources;
    final IdentityResolution resolution;
    final ObjectProvider<CustomerViewModel> detail;

    @Override
    public ListingData<CustomerRow> search(SearchRequest request, HttpRequest httpRequest) {
        var text = request.searchText() == null ? "" : request.searchText().toLowerCase(Locale.ROOT);
        var rows = customers.findAllByOrderByUpdatedAtDesc().stream()
                .filter(c -> c.aliasOf == null)
                .filter(c -> (c.id + " " + c.fullName() + " " + c.email + " " + c.documentNumber).toLowerCase(Locale.ROOT).contains(text))
                .map(c -> new CustomerRow(c.id, c.fullName(), c.email == null ? "" : c.email,
                        c.documentNumber == null ? "" : c.documentType + " " + c.documentNumber,
                        (int) sources.countByCustomerId(c.id), String.valueOf(c.salesforceState), status(c)))
                .toList();
        return new ListingData<>(new Page<>(request.searchText(), rows.size(), 0, rows.size(), rows));
    }

    static Status status(Customer c) {
        return c.status == CustomerStatus.CONSOLIDATED ? new Status(StatusType.SUCCESS, "Consolidated")
                : c.status == CustomerStatus.MERGED ? new Status(StatusType.NONE, "Merged")
                : new Status(StatusType.WARNING, "Provisional");
    }

    @Override
    public CustomerViewModel view(String id, HttpRequest httpRequest) {
        return detail.getObject().load(resolution.survivorOf(id));
    }
}
