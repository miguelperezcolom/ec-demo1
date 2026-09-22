package io.mateu.ecdemo1.mapping.ui.pages;

import io.mateu.ecdemo1.mapping.store.CauseRecord;
import io.mateu.ecdemo1.mapping.store.CauseRecordRepository;
import io.mateu.ecdemo1.mapping.store.CauseStatus;
import io.mateu.ecdemo1.mapping.store.WaiterRepository;
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

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
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

    final CauseRecordRepository causes;
    final WaiterRepository waiters;
    final ObjectProvider<CauseViewModel> detail;

    @Override
    public ListingData<CauseRow> search(SearchRequest request, HttpRequest httpRequest) {
        var text = request.searchText() == null ? "" : request.searchText().toLowerCase();
        var rows = causes.findAll().stream()
                .filter(c -> c.causeKey.toLowerCase().contains(text))
                .sorted(Comparator.comparing((CauseRecord c) -> c.status == CauseStatus.OPEN ? 0 : 1)
                        .thenComparing(c -> c.openedAt))
                .map(c -> new CauseRow(c.causeKey, c.type.name(), c.hotelCode, waiters.countWaitingOn(c.causeKey),
                        c.openedAt == null ? "" : WHEN.format(c.openedAt), c.openings,
                        c.status == CauseStatus.OPEN ? new Status(StatusType.WARNING, "Open")
                                : new Status(StatusType.SUCCESS, "Resolved")))
                .toList();
        return new ListingData<>(new Page<>(request.searchText(), rows.size(), 0, rows.size(), rows));
    }

    @Override
    public CauseViewModel view(String key, HttpRequest httpRequest) {
        return detail.getObject().load(causes.findById(key).orElseThrow(() -> new NoSuchElementException("No cause " + key)));
    }
}
