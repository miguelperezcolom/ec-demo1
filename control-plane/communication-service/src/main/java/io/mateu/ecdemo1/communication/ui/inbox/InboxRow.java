package io.mateu.ecdemo1.communication.ui.inbox;

import io.mateu.uidl.annotations.Details;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.data.ColumnActionGroup;
import io.mateu.uidl.data.Status;

/**
 * One line of the inbox. What it says in full is not a column but the row's detail: it opens when
 * the row is clicked, so the list stays one line per item.
 */
public record InboxRow(@Hidden String id, String since, Status kind, String hotel, String title,
                       @Details String detail,
                       @Label("Seen") boolean seen,
                       @Label("") ColumnActionGroup actions) {
}
