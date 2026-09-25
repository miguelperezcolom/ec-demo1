package io.mateu.ecdemo1.communication.ui.inbox;

import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Status;

public record InboxRow(String since, Status kind, String hotel, String title, String detail,
                       @Stereotype(FieldStereotype.html) String open) {
}
