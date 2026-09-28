package io.mateu.ecdemo1.communication.ui.pages;

import io.mateu.ecdemo1.communication.application.NotificationQueries;
import io.mateu.ecdemo1.uicommons.paging.DbPaging;
import io.mateu.ecdemo1.communication.store.DeliveryStatus;
import io.mateu.ecdemo1.communication.store.Notification;
import io.mateu.uidl.annotations.PageWidth;
import io.mateu.uidl.annotations.PageWidthStyle;
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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.NoSuchElementException;

/** Everything the integration asked to tell someone, newest first, and whether it went. */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Notifications")
@PageWidth(PageWidthStyle.EDGE_TO_EDGE)
public class NotificationsPage implements Listing<NotificationRow>, Searchable, Navigable<NotificationViewModel, String> {

    /** When, as a person reads it: local time to the minute — the year short, since history spans more than one. */
    static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm").withZone(ZoneId.of("Europe/Madrid"));

    final NotificationQueries notifications;
    final ObjectProvider<NotificationViewModel> detail;

    @Override
    public ListingData<NotificationRow> search(SearchRequest request, HttpRequest httpRequest) {
        return DbPaging.page(request, p -> notifications.find(request.searchText(), sorted(p, request)),
                n -> new NotificationRow(n.id, when(n.requestedAt), String.valueOf(n.type), n.hotelCode,
                        shortTitle(n.title), n.recipients, status(n), detail(n)));
    }

    /** Grid column → notification property: what a click on a column's header sorts by. */
    static final Map<String, String> SORTABLE = Map.of("requestedAt", "requestedAt", "type", "type",
            "hotel", "hotelCode", "title", "title", "status", "status");

    /** The page DbPaging asks for, in the order the grid asked for on the columns that map to one. */
    static Pageable sorted(Pageable page, SearchRequest request) {
        return PageRequest.of(page.getPageNumber(), page.getPageSize(),
                DbPaging.pageable(request.pageable(), SORTABLE).getSort());
    }

    static final int TITLE_LENGTH = 70;

    /** A title that fits a column: cut at a word, with an ellipsis, when it is longer than that. */
    static String shortTitle(String title) {
        if (title == null || title.length() <= TITLE_LENGTH) {
            return title;
        }
        var cut = title.lastIndexOf(' ', TITLE_LENGTH);
        return title.substring(0, cut > TITLE_LENGTH / 2 ? cut : TITLE_LENGTH).strip() + "…";
    }

    /** The whole title and what the notification says. */
    static String detail(Notification n) {
        var body = n.body == null || n.body.isBlank() ? "" : "\n\n" + n.body;
        return (n.title == null ? "" : n.title) + body;
    }

    static String when(Instant at) {
        return at == null ? "" : WHEN.format(at);
    }

    static Status status(Notification n) {
        return switch (n.status == null ? DeliveryStatus.FAILED : n.status) {
            case SENT -> new Status(StatusType.SUCCESS, "Sent");
            case FAILED -> new Status(StatusType.DANGER, "Failed (" + n.attempts + ")");
            case NO_RECIPIENTS -> new Status(StatusType.WARNING, "No recipients");
            case INBOX_ONLY -> new Status(StatusType.INFO, "Inbox only");
        };
    }

    @Override
    public NotificationViewModel view(String id, HttpRequest httpRequest) {
        return detail.getObject().load(notifications.byId(id).orElseThrow(() -> new NoSuchElementException("No notification " + id)));
    }
}
