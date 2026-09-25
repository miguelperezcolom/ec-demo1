package io.mateu.ecdemo1.audit.ui.pages;

import io.mateu.uidl.data.Status;

public record AuditRow(String when, String hotel, String user, String service, String action, Status outcome,
                       String parameters, String response) {
}
