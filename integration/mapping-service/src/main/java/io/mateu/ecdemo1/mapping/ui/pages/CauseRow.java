package io.mateu.ecdemo1.mapping.ui.pages;

import io.mateu.uidl.data.Status;

public record CauseRow(String key, String type, String hotel, long waiting, String since, int openings,
                       Status status) {
}
