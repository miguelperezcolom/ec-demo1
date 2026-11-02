package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.usecases.NewsletterService;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.PrimaryColumn;
import io.mateu.uidl.annotations.RowRoute;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import io.mateu.uidl.interfaces.Navigable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 * The newsletters the reader is in the audience of — whether they receive each, and its latest issue; a row
 * opens the newsletter's site (/intranet/n/{id}), where they subscribe, unsubscribe and read its archive.
 */
@Service
@RowRoute("/intranet/n/${row.id}")
@Scope("prototype")
@Title("Newsletters")
public class NewslettersListing implements Listing<NewslettersListing.Row>, Navigable<NewsletterSitePage, String> {

    public record Row(@Hidden String id, @Label("Newsletter") @PrimaryColumn(caption = "description") String name,
                      @Hidden @Label("Descripción") String description, @Label("Suscripción") Status subscription,
                      @Label("Último número") String latest) {
    }

    final PortalSupport portal;
    final NewsletterService newsletters;
    final ObjectProvider<NewsletterSitePage> pages;

    public NewslettersListing(PortalSupport portal, NewsletterService newsletters, ObjectProvider<NewsletterSitePage> pages) {
        this.portal = portal;
        this.newsletters = newsletters;
        this.pages = pages;
    }

    @Override
    public ListingData<Row> search(SearchRequest request, HttpRequest httpRequest) {
        var rows = newsletters.newslettersOf(portal.reader(httpRequest)).stream().map(s -> new Row(s.site().id(),
                s.site().name(), s.site().definition().description(),
                s.preference().subscribed() ? new Status(StatusType.SUCCESS, s.preference().sections().isEmpty()
                        ? "La recibes" : "La recibes (algunas secciones)") : new Status(StatusType.WARNING, "De baja"),
                s.latest() == null ? "" : "nº " + s.latest().number() + " · " + Ui.day(s.latest().sentAt()))).toList();
        return new ListingData<>(new Page<>("", Math.max(20, rows.size()), 0, rows.size(), rows),
                "No hay newsletters para ti");
    }

    /** Not reached from a row (@RowRoute opens the site's own route), but what makes it a crud. */
    @Override
    public NewsletterSitePage view(String id, HttpRequest httpRequest) {
        return pages.getObject().load("/intranet/n/" + id);
    }
}
