package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.usecases.ReaderActions;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/** "Mis favoritos": the reader's favourites crossed with the visibility function. */
@Service
@Scope("prototype")
@Title("Mis favoritos")
public class FavoritesListing extends VisibleListing {

    final ReaderActions actions;

    public FavoritesListing(PortalSupport portal, ObjectProvider<ItemPage> pages, ReaderActions actions) {
        super(portal, pages);
        this.actions = actions;
    }

    @Override
    public ListingData<ItemRow> search(SearchRequest request, HttpRequest httpRequest) {
        var reader = portal.reader(httpRequest);
        var page = actions.favorites(reader);
        return portal.rows(page, reader, 0, Math.max(20, page.content().size()), "",
                "Aún no tienes favoritos: usa «Favorito» en cualquier contenido");
    }
}
