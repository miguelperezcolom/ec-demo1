package io.mateu.ecdemo1.communication.ui.inbox;

import io.mateu.ecdemo1.communication.inbox.Inbox;
import io.mateu.ecdemo1.communication.store.InboxItem;
import io.mateu.ecdemo1.communication.ui.Paging;
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
import io.mateu.uidl.interfaces.Searchable;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * What waits for me: the notifications and tasks of my roles that nobody has resolved yet, newest
 * first. Each one links to where it is resolved; resolving it there takes it off this list.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Inbox")
public class InboxPage implements Listing<InboxRow>, Searchable, TriggersSupplier {

    static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.of("Europe/Madrid"));

    final Inbox inbox;

    @Override
    public ListingData<InboxRow> search(SearchRequest request, HttpRequest httpRequest) {
        var text = request.searchText() == null ? "" : request.searchText().trim().toLowerCase();
        var rows = inbox.openFor(Caller.roles(httpRequest)).stream()
                .filter(i -> text.isEmpty() || (i.title + " " + i.body + " " + i.hotelCode + " " + i.type).toLowerCase().contains(text))
                .map(InboxPage::row)
                .toList();
        return Paging.page(rows, request);
    }

    static InboxRow row(InboxItem i) {
        var kind = InboxItem.Kind.TASK.name().equals(i.kind) ? new Status(StatusType.INFO, "Task")
                : i.urgent ? new Status(StatusType.DANGER, "Urgent") : new Status(StatusType.WARNING, "To do");
        var open = i.link == null || i.link.isBlank() ? ""
                : "<a href=\"" + HtmlUtils.htmlEscape(i.link) + "\" target=\"_blank\" rel=\"noopener\">Open</a>";
        return new InboxRow(i.createdAt == null ? "" : WHEN.format(i.createdAt), kind, i.hotelCode, i.title, i.body, open);
    }

    @Override
    public List<Trigger> triggers(HttpRequest httpRequest) {
        return List.of(new OnLoadTrigger("search"));
    }
}
