package io.mateu.ecdemo1.loyalty.infra.in.ui;

import io.mateu.ecdemo1.loyalty.infra.in.ui.pages.AccrualsPage;
import io.mateu.ecdemo1.loyalty.infra.in.ui.pages.MemberCrud;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;

/** The entries of Riu Class. Neither is named "loyalty": an entry named like its section resolves the section. */
public class LoyaltyMenu {

    @Menu
    @Label("Socios")
    MemberCrud members;

    @Menu
    @Label("Acumulaciones")
    AccrualsPage accruals;
}
