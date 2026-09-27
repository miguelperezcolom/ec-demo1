package io.mateu.ecdemo1.mdm.ui.data;

import io.mateu.ecdemo1.mdm.store.ChangeRequest;

/** A change request as a row: its summary cut to a line, the whole of it in the detail. */
final class ChangeRequestRows {

    static final int SUMMARY = 80;

    private ChangeRequestRows() {
    }

    static ChangeRequestRow of(ChangeRequest r, String customer) {
        var changes = r.changes == null ? "" : r.changes;
        var detail = new StringBuilder(changes.isBlank() ? "Sin cambios respecto a los datos vigentes." : changes);
        if (r.sendError != null && !r.sendError.isBlank()) {
            detail.append("\n\nAún no ha llegado a Salesforce: ").append(r.sendError);
        }
        return new ChangeRequestRow(r.id, Estados.moment(r.requestedAt), customer, r.origin == null ? "" : r.origin,
                changes.length() > SUMMARY ? changes.substring(0, SUMMARY - 1) + "…" : changes,
                Estados.changeRequest(r.status), Estados.moment(r.decidedAt),
                r.salesforceCaseId == null ? "" : r.salesforceCaseId, detail.toString());
    }
}
