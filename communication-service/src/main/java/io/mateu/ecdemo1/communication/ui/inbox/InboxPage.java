package io.mateu.ecdemo1.communication.ui.inbox;

import io.mateu.ecdemo1.communication.inbox.Inbox;
import io.mateu.ecdemo1.communication.store.InboxItem;
import io.mateu.ecdemo1.communication.ui.Paging;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ListToolbarButton;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.Trigger;
import io.mateu.uidl.annotations.TriggerType;
import io.mateu.uidl.data.ColumnAction;
import io.mateu.uidl.data.ColumnActionGroup;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.data.UICommand;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import io.mateu.uidl.interfaces.Searchable;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What waits for me: the notifications and tasks that are mine — by name or by one of my roles — and
 * that nobody has resolved yet, newest
 * first. Clicking a row opens what it says in full; each one links to where it is resolved, and
 * resolving it there takes it off this list.
 *
 * <p>Seen is mine alone and resolves nothing: opening an item's link marks it, and so does "Mark
 * as seen" on the selected rows. What I have seen stays here, marked Seen, until it is
 * resolved — the badge in the shells counts only what I have not.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Inbox")
@Trigger(type = TriggerType.OnLoad, actionId = "search")
@Trigger(type = TriggerType.OnCustomEvent, actionId = "search", eventName = InboxPage.SEEN)
public class InboxPage implements Listing<InboxRow>, Searchable {

    /** Dispatched after marking something seen, so the listing searches again and shows it Seen. */
    static final String SEEN = "inbox-seen";

    static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.of("Europe/Madrid"));

    final Inbox inbox;

    @Override
    public ListingData<InboxRow> search(SearchRequest request, HttpRequest httpRequest) {
        var text = request.searchText() == null ? "" : request.searchText().trim().toLowerCase();
        var seen = inbox.seenBy(Caller.username(httpRequest));
        var rows = inbox.openFor(Caller.roles(httpRequest), Caller.username(httpRequest)).stream()
                .filter(i -> text.isEmpty() || (i.title + " " + i.body + " " + i.hotelCode + " " + i.type).toLowerCase().contains(text))
                .map(i -> row(i, seen))
                .toList();
        return Paging.page(rows, request);
    }

    static InboxRow row(InboxItem i, Set<String> seen) {
        var open = i.link == null || i.link.isBlank() ? new ColumnAction[0] : new ColumnAction[] {new ColumnAction("open", "Open")};
        return new InboxRow(i.id, i.createdAt == null ? "" : WHEN.format(i.createdAt), kind(i), i.hotelCode, i.title, i.body,
                seen.contains(i.id), new ColumnActionGroup(open));
    }

    static Status kind(InboxItem i) {
        return InboxItem.Kind.TASK.name().equals(i.kind) ? new Status(StatusType.INFO, "Task")
                : i.urgent ? new Status(StatusType.DANGER, "Urgent") : new Status(StatusType.WARNING, "To do");
    }

    @Override
    public boolean selectionEnabled() {
        return true;
    }

    /** The row's Open: marks it seen and goes where it is resolved. */
    public Object open(HttpRequest httpRequest) {
        var item = inbox.find(clickedId(httpRequest)).orElse(null);
        if (item == null || item.link == null || item.link.isBlank()) {
            return null;
        }
        inbox.markSeen(List.of(item.id), Caller.username(httpRequest));
        return List.of(UICommand.navigateTo(item.link), UICommand.dispatchEvent(SEEN));
    }

    /** Marks the selected rows seen. The work is in handleAction, like every action of this listing. */
    @ListToolbarButton
    @Label("Mark as seen")
    public void markAsSeen() {
    }

    @Override
    public boolean supportsAction(String actionId) {
        return "markAsSeen".equals(actionId) || Listing.super.supportsAction(actionId);
    }

    @Override
    public Object handleAction(String actionId, HttpRequest httpRequest) {
        if ("markAsSeen".equals(actionId)) {
            var ids = httpRequest.getListOfMaps("crud_selected_items").stream()
                    .map(row -> row.get("id")).filter(Objects::nonNull).map(String::valueOf).toList();
            inbox.markSeen(ids, Caller.username(httpRequest));
            return UICommand.dispatchEvent(SEEN);
        }
        return Listing.super.handleAction(actionId, httpRequest);
    }

    /** The id of the row an action was clicked on: the whole row in {@code _clickedRow}, or its id. */
    static String clickedId(HttpRequest httpRequest) {
        var parameters = httpRequest.runActionRq().parameters();
        if (parameters == null) {
            return null;
        }
        if (parameters.get("_clickedRow") instanceof Map<?, ?> row && row.get("id") != null) {
            return String.valueOf(row.get("id"));
        }
        var direct = parameters.get("id");
        return direct == null ? null : String.valueOf(direct);
    }
}
