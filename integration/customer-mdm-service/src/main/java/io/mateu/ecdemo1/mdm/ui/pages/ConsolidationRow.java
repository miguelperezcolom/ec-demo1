package io.mateu.ecdemo1.mdm.ui.pages;

import io.mateu.uidl.annotations.Details;
import io.mateu.uidl.data.Status;

/** The detail is long prose: not a column, it opens under the row when the row is clicked. */
public record ConsolidationRow(String absorbed, String survivor, String via, String received, int reservations,
                               Status propagation, @Details String detail) {
}
