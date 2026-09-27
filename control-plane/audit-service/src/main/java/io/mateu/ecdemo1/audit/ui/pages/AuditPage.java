package io.mateu.ecdemo1.audit.ui.pages;

import io.mateu.ecdemo1.audit.application.AuditQueries;
import io.mateu.ecdemo1.audit.application.AuditQuery;
import io.mateu.ecdemo1.uicommons.paging.DbPaging;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Pageable;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.fluent.OnLoadTrigger;
import io.mateu.uidl.fluent.Trigger;
import io.mateu.uidl.fluent.TriggersSupplier;
import io.mateu.uidl.interfaces.Filterable;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import io.mateu.uidl.interfaces.Searchable;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * The audit trail: every auditable action, newest first. The free text looks in every column —
 * parameters and responses included — and the filters narrow by date, hotel, user, action,
 * service and outcome. Only a listing: a record is not opened, edited or deleted.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Audited actions")
public class AuditPage implements Listing<AuditRow>, Searchable, Filterable<AuditFilters>, TriggersSupplier {

    static final int PAGE_SIZE = 50;

    /** Grid column → record property: what a click on a column's header sorts by. */
    static final Map<String, String> SORTABLE = Map.of("when", "at", "hotel", "hotelCode", "user", "actor",
            "service", "service", "action", "action", "outcome", "succeeded");

    final AuditQueries queries;

    @Value("${audit.zone:Europe/Madrid}")
    String zone;

    @Override
    public ListingData<AuditRow> search(SearchRequest request, HttpRequest httpRequest) {
        var zoneId = ZoneId.of(zone);
        var found = queries.find(query(request.searchText(), filters(request)), pageable(request.pageable()));
        var when = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(zoneId);
        var listing = DbPaging.listing(request.searchText(), found, r -> new AuditRow(when.format(r.at), r.hotelCode,
                r.actor, r.service, r.action, r.succeeded ? new Status(StatusType.SUCCESS, "Carried out")
                        : new Status(StatusType.DANGER, "Refused"), r.parameters, r.response));
        return new ListingData<>(listing.page(), "No audited action matches");
    }

    /** The page asked for — {@value #PAGE_SIZE} rows when it does not say — sorted by the columns that map to one. */
    static org.springframework.data.domain.Pageable pageable(Pageable pageable) {
        var sized = pageable == null || pageable.size() <= 0
                ? new Pageable(pageable == null ? 0 : pageable.page(), PAGE_SIZE, pageable == null ? List.of() : pageable.sort())
                : pageable;
        return DbPaging.pageable(sized, SORTABLE);
    }

    /** The screen's search, as the trail's query. */
    static AuditQuery query(String text, AuditFilters filters) {
        if (filters == null) {
            return new AuditQuery(text, null, null, null, null, null, null, null);
        }
        return new AuditQuery(text, filters.hotel, filters.user, filters.action, filters.service,
                filters.when == null ? null : filters.when.from(), filters.when == null ? null : filters.when.to(),
                // both outcomes, or neither, is either
                filters.outcome == null || filters.outcome.size() != 1 ? null
                        : filters.outcome.contains(AuditFilters.Outcome.CARRIED_OUT));
    }

    /** Not navigable, so it would not search on opening by itself. */
    @Override
    public List<Trigger> triggers(HttpRequest httpRequest) {
        return List.of(new OnLoadTrigger("search"));
    }
}
