package io.mateu.ecdemo1.mdm.ui.pages;

import io.mateu.uidl.data.Status;

public record CustomerRow(String id, String name, String email, String document, int reservations, String salesforce,
                          Status status) {
}
