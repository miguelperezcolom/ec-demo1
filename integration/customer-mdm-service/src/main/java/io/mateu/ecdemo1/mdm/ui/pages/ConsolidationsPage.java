package io.mateu.ecdemo1.mdm.ui.pages;

import io.mateu.ecdemo1.mdm.application.ConsolidationQueries;
import io.mateu.ecdemo1.uicommons.paging.DbPaging;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.fluent.OnLoadTrigger;
import io.mateu.uidl.fluent.Trigger;
import io.mateu.uidl.fluent.TriggersSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** What Salesforce's cleaning concluded, newest first, and whether the new codes reached the PMS. */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Consolidations")
public class ConsolidationsPage implements Listing<ConsolidationRow>, TriggersSupplier {

    /** Grid column → consolidation property: what a click on a column's header sorts by. */
    static final Map<String, String> SORTABLE = Map.of("absorbed", "absorbedId", "survivor", "survivorId", "via", "via",
            "received", "receivedAt", "reservations", "reservations");

    final ConsolidationQueries consolidations;

    @Override
    public ListingData<ConsolidationRow> search(SearchRequest request, HttpRequest httpRequest) {
        return DbPaging.page(request, p -> consolidations.page(PageRequest.of(p.getPageNumber(), p.getPageSize(),
                        DbPaging.pageable(request.pageable(), SORTABLE).getSort())),
                c -> new ConsolidationRow(c.absorbedId, c.survivorId == null ? "—" : c.survivorId, c.via,
                        String.valueOf(c.receivedAt), c.reservations,
                        c.survivorId == null ? new Status(StatusType.NONE, "Removed")
                                : c.propagatedAt != null ? new Status(StatusType.SUCCESS, "Propagated")
                                : new Status(StatusType.WARNING, "Pending"),
                        c.detail == null ? "" : c.detail));
    }

    /** A listing that is not navigable does not search on its own when the page loads. */
    @Override
    public List<Trigger> triggers(HttpRequest httpRequest) {
        return List.of(new OnLoadTrigger("search"));
    }
}
