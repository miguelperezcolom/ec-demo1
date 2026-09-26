package io.mateu.ecdemo1.communication.ui.inbox;

import io.mateu.ecdemo1.communication.inbox.Inbox;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.Trigger;
import io.mateu.uidl.annotations.TriggerType;
import io.mateu.uidl.annotations.UI;
import io.mateu.uidl.data.State;
import io.mateu.uidl.data.Text;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.ComponentTreeSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Hydratable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The shells' widget: how many things wait for me, and a way to them. It replaces the forms engine's
 * "my tasks" badge — a task is one of the things that wait, next to the notifications that link to
 * a screen. Refreshed every few seconds.
 *
 * <p>It counts what I have not seen yet — like unread mail, so it says when something new arrived —
 * and says "urgent" only of those. What I have seen is still in the inbox until it is resolved; the
 * link goes there all the same.
 */
@UI("/_inbox/badge")
@Title("")
@Service
@RequiredArgsConstructor
@Trigger(type = TriggerType.OnLoad, actionId = "refresh", timeoutMillis = 10000)
@Trigger(type = TriggerType.OnSuccess, actionId = "refresh", calledActionId = "refresh", timeoutMillis = 10000)
@Action(id = "refresh")
public class InboxBadge implements Hydratable, ComponentTreeSupplier {

    final Inbox inbox;

    String content = "";

    Object refresh() {
        return new State(this);
    }

    @Override
    public void hydrate(HttpRequest httpRequest) {
        var seen = inbox.seenBy(Caller.username(httpRequest));
        var waiting = inbox.openFor(Caller.roles(httpRequest), Caller.username(httpRequest)).stream().filter(i -> !seen.contains(i.id)).toList();
        var urgent = waiting.stream().filter(i -> i.urgent).count();
        var go = "event.preventDefault(); this.dispatchEvent(new CustomEvent('navigation-requested', {"
                + "detail: {route: '/inbox/pending', consumedRoute: '', baseUrl: '/_inbox', uriPrefix: '',"
                + " serverSideType: '" + InboxHome.class.getName() + "'}, bubbles: true, composed: true}))";
        var label = waiting.isEmpty() ? "Inbox" : "Inbox (" + waiting.size() + (urgent > 0 ? ", " + urgent + " urgent" : "") + ")";
        // The bell is Vaadin's own icon (the shells that draw this widget load vaadin-icons), sized to
        // the text beside it; in the error colour while something urgent waits.
        var bell = "<vaadin-icon icon=\"vaadin:bell\" style=\"width: 1em; height: 1em; vertical-align: -0.125em;"
                + " margin-inline-end: 0.3em;" + (urgent > 0 ? " color: var(--lumo-error-color);" : "") + "\"></vaadin-icon>";
        content = "<a href=\"#\" onclick=\"" + go + "\" style=\"text-decoration: none; white-space: nowrap;"
                + (urgent > 0 ? " font-weight: 600;" : "") + "\">" + bell
                // A narrow header keeps the bell and the count; the header says which through a CSS variable.
                + "<span style=\"display: var(--mateu-header-wide-only, inline)\">" + label + "</span>"
                + (waiting.isEmpty() ? "" : "<span style=\"display: var(--mateu-header-narrow-only, none)\">" + waiting.size() + "</span>")
                + "</a>&nbsp;&nbsp;";
    }

    @Override
    public Component component(HttpRequest httpRequest) {
        return Text.builder().text("${state.content}").build();
    }
}
