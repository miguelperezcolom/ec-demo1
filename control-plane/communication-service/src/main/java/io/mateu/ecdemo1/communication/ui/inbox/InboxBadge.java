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
 * and says "urgente" only of those. What I have seen is still in the inbox until it is resolved; the
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

    // Public: since Mateu 386 an unmarked method is an action only if it is public.
    public Object refresh() {
        return new State(this);
    }

    @Override
    public void hydrate(HttpRequest httpRequest) {
        var seen = inbox.seenBy(Caller.username(httpRequest));
        var waiting = inbox.openFor(Caller.roles(httpRequest), Caller.username(httpRequest)).stream().filter(i -> !seen.contains(i.id)).toList();
        var urgent = waiting.stream().filter(i -> i.urgent).count();
        content = html(waiting.size(), urgent);
    }

    /** What the badge says, in full: its tooltip and its accessible name. "Bandeja (2 · 1 urgente)". */
    static String label(int waiting, long urgent) {
        if (waiting == 0) {
            return "Bandeja";
        }
        return "Bandeja (" + waiting + (urgent > 0 ? " · " + urgent + (urgent == 1 ? " urgente" : " urgentes") : "") + ")";
    }

    /** Where the badge goes: the inbox's pending items, a route of the inbox's own pod. */
    static final String ROUTE = "/inbox/pending";

    /**
     * The badge as the header's other icon buttons: a tertiary icon button with an outline bell in
     * the header's icon colour, and the count as a small pill over the bell's top end — red while
     * something urgent waits. The words are in its tooltip and accessible name, not on the bar.
     *
     * <p>It is HTML because both shells draw it from the same string: Redwood draws a remote header
     * widget from its {@code Text}s alone, so a Mateu button or element would not be there at all.
     * The Vaadin shell upgrades {@code <vaadin-button>} into a Lumo button (size, hover, focus ring).
     * The Redwood shell has no such element: there it stays a plain focusable element ({@code role},
     * {@code tabindex}), its {@code <vaadin-icon>} becomes the Redwood bell, and the colours fall back
     * to the header's own (the {@code --lumo-*} ones are not defined there, except the urgent red,
     * which the Redwood header sets to its light danger tone).
     *
     * <p>No inline handlers: Mateu sanitises rendered HTML, and an {@code onclick} does not survive
     * it. The {@code data-ec-route} attributes say where it goes, and the inbox's script, which every
     * shell loads ({@code /_inbox/push/push.js}), turns a click on it — or Enter or Space where it is
     * not a real button — into the same {@code navigation-requested} the menu sends: an in-app
     * navigation in both renderers, to a route of this pod.
     */
    static String html(int waiting, long urgent) {
        var label = label(waiting, urgent);
        var bell = "<vaadin-icon icon=\"vaadin:bell-o\" style=\"width: var(--lumo-icon-size-m, 1.25em);"
                + " height: var(--lumo-icon-size-m, 1.25em);\"></vaadin-icon>";
        var count = waiting == 0 ? "" : "<span aria-hidden=\"true\" style=\"position: absolute; top: 0.125rem;"
                + " inset-inline-end: 0.0625rem; box-sizing: border-box; min-width: 1.15em; height: 1.15em;"
                + " padding: 0 0.3em; border-radius: 1em; font-size: var(--lumo-font-size-xxs, 0.6875rem);"
                + " font-weight: 600; line-height: 1.15em; text-align: center; pointer-events: none;"
                + (urgent > 0
                        ? " background: var(--lumo-error-color, #c62828); color: var(--lumo-error-contrast-color, #161513);"
                        : " background: var(--lumo-primary-color, currentColor); color: var(--lumo-primary-contrast-color, #161513);")
                + "\">" + (waiting > 99 ? "99+" : String.valueOf(waiting)) + "</span>";
        return "<vaadin-button theme=\"tertiary icon\" role=\"button\" tabindex=\"0\""
                + " aria-label=\"" + label + "\" title=\"" + label + "\""
                + " data-ec-route=\"" + ROUTE + "\" data-ec-base-url=\"/_inbox\""
                + " data-ec-server-side-type=\"" + InboxHome.class.getName() + "\""
                + " style=\"position: relative; display: inline-flex; align-items: center; justify-content: center;"
                + " min-width: var(--lumo-size-m, 2.25rem); height: var(--lumo-size-m, 2.25rem); margin: 0; cursor: pointer;"
                + " color: var(--mateu-header-icon-color, var(--lumo-secondary-text-color, currentColor));\">"
                + bell + count + "</vaadin-button>";
    }

    @Override
    public Component component(HttpRequest httpRequest) {
        return Text.builder().text("${state.content}").build();
    }
}
