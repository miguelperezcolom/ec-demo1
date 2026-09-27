package io.mateu.ecdemo1.integrations.ui.pages;

import io.mateu.uidl.data.Status;

/** A pms-fo integration is read by its PMS property, and that is how it is addressed. */
public record FrontOfficeIntegrationRow(String property, String frontOffice, String name, Status status,
                                        String waitingFor, String backfill, String lastPoll) {
}
