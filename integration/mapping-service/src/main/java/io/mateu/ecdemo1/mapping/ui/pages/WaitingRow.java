package io.mateu.ecdemo1.mapping.ui.pages;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.data.ColumnActionGroup;

/**
 * A process waiting on the cause — or released by it and not answering: both can be discarded, from
 * the row itself.
 *
 * @param state  WAITING, or RELEASED when its causes were resolved and it has not answered yet
 * @param engine the engine's id of the process, or "unknown" when the wait was registered without it
 *               (discarding it then cannot cancel it: that is done by hand in Admin → Processes)
 */
public record WaitingRow(String process, String definition, String subject, String since, String state,
                         String engine, @Label("") ColumnActionGroup actions) {
}
