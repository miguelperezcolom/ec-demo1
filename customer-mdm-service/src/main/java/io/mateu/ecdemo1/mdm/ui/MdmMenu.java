package io.mateu.ecdemo1.mdm.ui;

import io.mateu.ecdemo1.mdm.ui.pages.ConsolidationsPage;
import io.mateu.ecdemo1.mdm.ui.pages.CustomersPage;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;

public class MdmMenu {

    /**
     * Named "golden" and labelled, not named "customers": the entry and its section would otherwise
     * share a name, and /customers/customers resolves the section instead of the screen.
     */
    @Menu
    @Label("Golden records")
    CustomersPage golden;

    @Menu
    ConsolidationsPage consolidations;
}
