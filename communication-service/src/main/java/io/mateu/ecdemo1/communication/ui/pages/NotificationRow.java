package io.mateu.ecdemo1.communication.ui.pages;

import io.mateu.uidl.annotations.Details;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.data.Status;

/**
 * One notification. The title is cut to a line so the list stays one line per notification; the whole
 * of it, and what it says, open under the row.
 */
public record NotificationRow(String id, @Label("Requested") String requestedAt, String type, String hotel, String title,
                              String recipients, Status status, @Details String detail) {
}
