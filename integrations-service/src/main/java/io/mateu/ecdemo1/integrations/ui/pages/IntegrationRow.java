package io.mateu.ecdemo1.integrations.ui.pages;

import io.mateu.uidl.data.Status;

public record IntegrationRow(String id, String crsHotel, String operaProperty, String name, Status status,
                             String waitingFor, String backfill) {
}
