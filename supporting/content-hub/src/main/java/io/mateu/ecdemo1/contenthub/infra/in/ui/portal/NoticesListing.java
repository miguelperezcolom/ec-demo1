package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.out.ContentQuery;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope("prototype")
@Title("Avisos")
public class NoticesListing extends VisibleListing {

    public NoticesListing(PortalSupport portal, ObjectProvider<ItemPage> pages) {
        super(portal, pages);
    }

    @Override
    public ListingData<ItemRow> search(SearchRequest request, HttpRequest httpRequest) {
        return portal.listing(httpRequest, request, ContentQuery.of(io.mateu.ecdemo1.contenthub.domain.item.ItemType.NOTICE, 0, 20), "No hay avisos para ti");
    }
}
