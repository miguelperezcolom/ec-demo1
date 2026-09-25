package io.mateu.ecdemo1.integrations.ui.pages;

import io.mateu.uidl.data.Status;

/** A row is read by its hotel, and that is also how it is addressed: one integration per CRS hotel. */
public record IntegrationRow(String crsHotel, String operaProperty, String name, Status status,
                             String waitingFor, String backfill) {
}
