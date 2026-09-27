package io.mateu.ecdemo1.mapping.ui.pages;

import io.mateu.uidl.data.Status;

public record EntryRow(String id, String type, String scope, String crsCode, String pmsCode, Integer version,
                       Status status, String proposedBy, String decidedBy) {
}
