package io.mateu.ecdemo1.communication.ui.pages;

import io.mateu.ecdemo1.communication.store.DeliveryStatus;
import io.mateu.ecdemo1.communication.store.Notification;
import io.mateu.ecdemo1.communication.store.NotificationRepository;
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

import java.util.NoSuchElementException;

/** Everything the integration asked to tell someone, newest first, and whether it went. */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Notifications")
public class NotificationsPage implements Listing<NotificationRow>, Searchable, Navigable<NotificationViewModel, String> {

    final NotificationRepository notifications;
    final ObjectProvider<NotificationViewModel> detail;

    @Override
    public ListingData<NotificationRow> search(SearchRequest request, HttpRequest httpRequest) {
        var text = request.searchText() == null ? "" : request.searchText().toLowerCase();
        var rows = notifications.findAllByOrderByRequestedAtDesc().stream()
                .filter(n -> (n.title + " " + n.hotelCode + " " + n.type).toLowerCase().contains(text))
                .map(n -> new NotificationRow(n.id, String.valueOf(n.requestedAt), String.valueOf(n.type), n.hotelCode,
                        n.title, n.recipients, status(n)))
                .toList();
        return new ListingData<>(new Page<>(request.searchText(), rows.size(), 0, rows.size(), rows));
    }

    static Status status(Notification n) {
        return switch (n.status == null ? DeliveryStatus.FAILED : n.status) {
            case SENT -> new Status(StatusType.SUCCESS, "Sent");
            case FAILED -> new Status(StatusType.DANGER, "Failed (" + n.attempts + ")");
            case NO_RECIPIENTS -> new Status(StatusType.WARNING, "No recipients");
        };
    }

    @Override
    public NotificationViewModel view(String id, HttpRequest httpRequest) {
        return detail.getObject().load(notifications.findById(id).orElseThrow(() -> new NoSuchElementException("No notification " + id)));
    }
}
