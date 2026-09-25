package io.mateu.ecdemo1.audit.ui;

import io.mateu.ecdemo1.audit.ui.pages.AuditPage;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;

/**
 * A section of one entry, as the other federated services have: without it the Redwood shell lifts
 * the lone entry into the bar ("Audited actions") where Vaadin shows the section ("Audit").
 */
public class AuditMenu {

    @Menu
    @Label("Audited actions")
    AuditPage actions;
}
