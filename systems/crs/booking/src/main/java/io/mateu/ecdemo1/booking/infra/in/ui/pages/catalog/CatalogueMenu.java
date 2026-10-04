package io.mateu.ecdemo1.booking.infra.in.ui.pages.catalog;

import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Menu;

/** Call center → Catalogue: the CRS's codes the call center can change — today, a hotel's rate plans. */
public class CatalogueMenu {

    @Menu
    @Label("Rate plans")
    RatePlansCrud ratePlans;
}
