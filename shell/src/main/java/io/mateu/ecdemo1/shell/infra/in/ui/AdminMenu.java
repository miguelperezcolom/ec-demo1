package io.mateu.ecdemo1.shell.infra.in.ui;

import io.mateu.uidl.annotations.Menu;
import io.mateu.uidl.data.RemoteMenu;

/**
 * Running the platform, gathered behind one entry so the menu bar can say what this console is for.
 *
 * <p>What is left beside it — Booking — is the product: the thing somebody uses to get their job
 * done. Workflow, Forms and Worker are how the platform underneath is driven, and on
 * a bar that shows all five at the same level the difference is invisible.
 *
 * <p>Note this is the DATA plane's admin, and it is not the control console. The line there is
 * configuration versus work; the line here is the product versus the machinery that runs it. The
 * two consoles both have a "Workflow" and they still mean different things — processes in flight
 * here, definitions and analytics there.
 *
 * <p>The label on this group is the field name, and unlike the entries inside it there is no remote
 * pod to answer with one of its own — which is exactly why it can be invented here without the menu
 * bar changing under the reader a moment after it is drawn.
 */
public class AdminMenu {

    /** Running processes. */
    @Menu
    RemoteMenu workflow = new RemoteMenu("/_workflow").withLabel("Workflow");

    /** Form executions and the human tasks waiting to be done. */
    @Menu
    RemoteMenu forms = new RemoteMenu("/_forms").withLabel("Forms");

    /** What the test worker was handed, and the overrides that answer it by hand. */
    @Menu
    RemoteMenu worker = new RemoteMenu("/_worker").withLabel("Worker");
}
