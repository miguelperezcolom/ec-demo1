package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import io.mateu.uidl.annotations.RowRoute;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;

/** A portal listing whose rows open the item: everything it lists comes through the visibility function. */
@RowRoute(Ui.PORTAL + "/noticias/${row.id}")
public abstract class VisibleListing implements Listing<ItemRow> {

    protected final PortalSupport portal;

    protected VisibleListing(PortalSupport portal) {
        this.portal = portal;
    }

}
