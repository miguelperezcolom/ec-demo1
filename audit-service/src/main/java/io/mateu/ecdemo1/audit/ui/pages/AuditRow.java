package io.mateu.ecdemo1.audit.ui.pages;

import io.mateu.uidl.annotations.Details;
import io.mateu.uidl.data.Status;

/** One audited action. The parameters are long, so they open under the row instead of filling a column. */
public record AuditRow(String when, String hotel, String user, String service, String action, Status outcome,
                       @Details String parameters, String response) {
}
