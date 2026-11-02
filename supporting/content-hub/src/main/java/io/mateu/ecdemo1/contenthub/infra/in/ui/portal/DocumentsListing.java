package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.uidl.interfaces.Searchable;
import io.mateu.ecdemo1.contenthub.application.out.ContentQuery;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope("prototype")
@Title("Documentos y enlaces")
public class DocumentsListing extends VisibleListing implements Searchable {

    public DocumentsListing(PortalSupport portal) {
        super(portal);
    }

    @Override
    public ListingData<ItemRow> search(SearchRequest request, HttpRequest httpRequest) {
        return portal.listing(httpRequest, request, new ContentQuery(java.util.Set.of(io.mateu.ecdemo1.contenthub.domain.item.ItemType.DOCUMENT, io.mateu.ecdemo1.contenthub.domain.item.ItemType.LINK), null, null, null, 0, 20), "No hay documentos para ti");
    }
}
