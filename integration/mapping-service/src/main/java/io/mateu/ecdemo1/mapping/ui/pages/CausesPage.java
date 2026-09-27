package io.mateu.ecdemo1.mapping.ui.pages;

import io.mateu.ecdemo1.mapping.queries.CauseQueries;
import io.mateu.ecdemo1.mapping.store.CauseStatus;
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
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.NoSuchElementException;

/**
 * What is blocking processes, and how many wait behind each cause — open ones first, oldest first.
 * The count is the point: three thousand reservations behind one missing board code is one line.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Causes")
public class CausesPage implements Listing<CauseRow>, Searchable, Navigable<CauseViewModel, String> {

    static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());

    final CauseQueries causes;
    final ObjectProvider<CauseViewModel> detail;

    /** Filtered by the cause's key, ordered (open first, oldest first) and paged by the database. */
    @Override
    public ListingData<CauseRow> search(SearchRequest request, HttpRequest httpRequest) {
        return DbPaging.page(request, p -> causes.page(request.searchText(), p), c -> new CauseRow(c.causeKey,
                c.type.name(), c.hotelCode, causes.waitingOn(c.causeKey),
                c.openedAt == null ? "" : WHEN.format(c.openedAt), c.openings,
                c.status == CauseStatus.OPEN ? new Status(StatusType.WARNING, "Open")
                        : new Status(StatusType.SUCCESS, "Resolved")));
    }

    @Override
    public CauseViewModel view(String key, HttpRequest httpRequest) {
        return detail.getObject().load(causes.cause(key).orElseThrow(() -> new NoSuchElementException("No cause " + key)));
    }
}
