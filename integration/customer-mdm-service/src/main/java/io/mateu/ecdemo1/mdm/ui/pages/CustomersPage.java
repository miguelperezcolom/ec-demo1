package io.mateu.ecdemo1.mdm.ui.pages;

import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.mdm.resolution.IdentityResolution;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.application.CustomerQueries;
import io.mateu.ecdemo1.uicommons.paging.DbPaging;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * The golden records: who the customers are, as the MDM holds them. Absorbed ones are not listed —
 * they are aliases, and their code opens the survivor.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Golden records")
public class CustomersPage implements Listing<CustomerRow>, Searchable, Navigable<CustomerViewModel, String> {

    /** Grid column → customer property: what a click on a column's header sorts by. */
    static final Map<String, String> SORTABLE = Map.of("id", "id", "email", "email", "document", "documentNumber",
            "salesforce", "salesforceState", "status", "status");

    final CustomerQueries customers;
    final IdentityResolution resolution;
    final ObjectProvider<CustomerViewModel> detail;

    @Override
    public ListingData<CustomerRow> search(SearchRequest request, HttpRequest httpRequest) {
        return DbPaging.page(request, p -> customers.goldenRecords(request.searchText(), PageRequest.of(p.getPageNumber(),
                        p.getPageSize(), DbPaging.pageable(request.pageable(), SORTABLE).getSort())),
                c -> new CustomerRow(c.id, c.fullName(), c.email == null ? "" : c.email,
                        c.documentNumber == null ? "" : c.documentType + " " + c.documentNumber,
                        (int) customers.countSources(c.id), String.valueOf(c.salesforceState), status(c)));
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
