package io.mateu.ecdemo1.erp.infra.in.ui.pages;

import io.mateu.uidl.data.Status;

public record PartnerRow(String code, String name, String type, String billingMode, Status status, long version) {
}
