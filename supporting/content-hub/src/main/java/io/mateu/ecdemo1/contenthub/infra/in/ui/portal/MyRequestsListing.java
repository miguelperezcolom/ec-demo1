package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.usecases.RequestService;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import io.mateu.uidl.annotations.RowRoute;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/** The reader's requests, newest first, with where each one is. A row opens it, with its history. */
@RowRoute("/intranet/solicitudes/${row.id}")
@Service
@Scope("prototype")
@Title("Mis solicitudes")
public class MyRequestsListing implements Listing<RequestRow> {

    final PortalSupport portal;
    final RequestService requests;

    public MyRequestsListing(PortalSupport portal, RequestService requests) {
        this.portal = portal;
        this.requests = requests;
    }

    @Override
    public ListingData<RequestRow> search(SearchRequest request, HttpRequest httpRequest) {
        var rows = requests.mine(portal.reader(httpRequest)).stream()
                .map(r -> new RequestRow(r.id(), r.type().label(), RequestComponents.dates(r), RequestComponents.state(r), Ui.day(r.createdAt())))
                .toList();
        return new ListingData<>(new Page<>("", Math.max(20, rows.size()), 0, rows.size(), rows),
                "No has hecho ninguna solicitud. Hazla en «Nueva solicitud».");
    }

}
