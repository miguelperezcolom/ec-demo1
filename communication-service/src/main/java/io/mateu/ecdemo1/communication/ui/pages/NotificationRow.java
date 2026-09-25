package io.mateu.ecdemo1.communication.ui.pages;

import io.mateu.uidl.data.Status;

public record NotificationRow(String id, String requestedAt, String type, String hotel, String title, String recipients,
                              Status status) {
}
