package io.mateu.ecdemo1.mdm.ui.pages;

import io.mateu.uidl.data.Status;

public record ConsolidationRow(String absorbed, String survivor, String via, String received, int reservations,
                               Status propagation, String detail) {
}
