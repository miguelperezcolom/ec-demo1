package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.out.ContentQuery;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;

/** A section's visible content, newest first. */
@Service
@Scope("prototype")
public class SectionPage implements Identifiable {

    @Hidden
    String id;
    @Hidden
    String title;
    @Hidden
    String html;

    @Label("")
    @Colspan(2)
    Callable<Component> content = () -> Html.block(html == null ? "" : html);

    final PortalSupport portal;
    final NavigationRepository navigation;

    public SectionPage(PortalSupport portal, NavigationRepository navigation) {
        this.portal = portal;
        this.navigation = navigation;
    }

    public SectionPage load(String id, HttpRequest httpRequest) {
        var reader = portal.reader(httpRequest);
        this.id = id;
        var language = reader.attributes().language();
        title = navigation.get().find(id).map(s -> s.name(language)).orElse(id);
        var items = portal.latest(reader, ContentQuery.section(id, 0, 50));
        html = ItemPage.banner(reader.viewingAs())
                + (items.isEmpty() ? "<p>No hay nada aquí que puedas ver.</p>" : PageRenderer.list(items));
        return this;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return title == null ? "Sección" : title;
    }
}
