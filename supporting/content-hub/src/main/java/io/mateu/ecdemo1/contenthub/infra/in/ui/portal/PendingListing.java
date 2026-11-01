package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.out.ContentIndex;
import io.mateu.ecdemo1.contenthub.application.usecases.ReaderActions;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/** What the reader must confirm having read and has not, in its current version. */
@Service
@Scope("prototype")
@Title("Pendientes de confirmar")
public class PendingListing extends VisibleListing {

    final ReaderActions actions;

    public PendingListing(PortalSupport portal, ObjectProvider<ItemPage> pages, ReaderActions actions) {
        super(portal, pages);
        this.actions = actions;
    }

    @Override
    public ListingData<ItemRow> search(SearchRequest request, HttpRequest httpRequest) {
        var reader = portal.reader(httpRequest);
        var pending = actions.pendingConfirmations(reader);
        return portal.rows(new ContentIndex.Page<>(pending, pending.size()), reader, 0, Math.max(20, pending.size()), "",
                "No tienes nada pendiente de confirmar");
    }
}
