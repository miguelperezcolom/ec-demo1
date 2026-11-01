package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.usecases.RequestService;
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

/**
 * The requests the reader has to decide — as the manager of whoever asked (or as their manager's
 * manager, once it escalated), or as HR. From the workflow engine's task inbox, as the reader.
 */
@Service
@Scope("prototype")
@Title("Por aprobar")
public class ApprovalsListing implements Listing<ApprovalRow>, Navigable<ApprovalPage, String> {

    final PortalSupport portal;
    final RequestService requests;
    final ObjectProvider<ApprovalPage> pages;

    public ApprovalsListing(PortalSupport portal, RequestService requests, ObjectProvider<ApprovalPage> pages) {
        this.portal = portal;
        this.requests = requests;
        this.pages = pages;
    }

    @Override
    public ListingData<ApprovalRow> search(SearchRequest request, HttpRequest httpRequest) {
        portal.reader(httpRequest);
        var rows = requests.approvals(httpRequest.getHeaderValue("Authorization")).stream()
                .map(a -> new ApprovalRow(a.task().id(), a.request().employeeName(), a.request().type().label(),
                        RequestHtml.dates(a.request()), a.hr() ? "RRHH" : "Responsable",
                        a.task().dueDate() == null ? "" : a.task().dueDate().substring(0, Math.min(10, a.task().dueDate().length()))))
                .toList();
        return new ListingData<>(new Page<>("", Math.max(20, rows.size()), 0, rows.size(), rows), "Nada que aprobar");
    }

    @Override
    public ApprovalPage view(String id, HttpRequest httpRequest) {
        return pages.getObject().load(id, httpRequest);
    }
}
