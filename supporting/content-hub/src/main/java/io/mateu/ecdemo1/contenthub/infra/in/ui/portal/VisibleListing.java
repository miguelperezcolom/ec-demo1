package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import io.mateu.uidl.interfaces.Navigable;
import org.springframework.beans.factory.ObjectProvider;

/** A portal listing whose rows open the item: everything it lists comes through the visibility function. */
public abstract class VisibleListing implements Listing<ItemRow>, Navigable<ItemPage, String> {

    protected final PortalSupport portal;
    protected final ObjectProvider<ItemPage> pages;

    protected VisibleListing(PortalSupport portal, ObjectProvider<ItemPage> pages) {
        this.portal = portal;
        this.pages = pages;
    }

    @Override
    public ItemPage view(String id, HttpRequest httpRequest) {
        return pages.getObject().load(id, httpRequest);
    }
}
