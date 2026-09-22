package io.mateu.ecdemo1.operamock.ui.pages;

import io.mateu.uidl.data.Status;

public record ReservationRow(String id, String hotel, String crsLocator, String arrival, String departure,
                             String roomType, String ratePlan, String packageCode, String crsVersion,
                             int deposits, Status status) {
}
