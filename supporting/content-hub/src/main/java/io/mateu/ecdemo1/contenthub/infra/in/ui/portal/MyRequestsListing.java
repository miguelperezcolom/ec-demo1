package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.usecases.RequestService;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
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

/** The reader's requests, newest first, with where each one is. A row opens it, with its history. */
@Service
@Scope("prototype")
@Title("Mis solicitudes")
public class MyRequestsListing implements Listing<RequestRow>, Navigable<RequestPage, String> {

    final PortalSupport portal;
    final RequestService requests;
    final ObjectProvider<RequestPage> pages;

    public MyRequestsListing(PortalSupport portal, RequestService requests, ObjectProvider<RequestPage> pages) {
        this.portal = portal;
        this.requests = requests;
        this.pages = pages;
    }

    @Override
    public ListingData<RequestRow> search(SearchRequest request, HttpRequest httpRequest) {
        var rows = requests.mine(portal.reader(httpRequest)).stream()
                .map(r -> new RequestRow(r.id(), r.type().label(), RequestHtml.dates(r), r.status().label(), Ui.day(r.createdAt())))
                .toList();
        return new ListingData<>(new Page<>("", Math.max(20, rows.size()), 0, rows.size(), rows),
                "No has hecho ninguna solicitud. Hazla en «Nueva solicitud».");
    }

    @Override
    public RequestPage view(String id, HttpRequest httpRequest) {
        return pages.getObject().load(id, httpRequest);
    }
}
