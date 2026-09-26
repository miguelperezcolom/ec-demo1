package io.mateu.ecdemo1.communication.ui.pages;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.data.Status;

public record NotificationRow(String id, @Label("Requested") String requestedAt, String type, String hotel, String title, String recipients,
                              Status status) {
}
