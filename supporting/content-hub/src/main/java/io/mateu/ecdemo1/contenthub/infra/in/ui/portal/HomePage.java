package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.out.ContentQuery;
import io.mateu.ecdemo1.contenthub.application.out.HomePages;
import io.mateu.ecdemo1.contenthub.application.out.VisibleItem;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.ReaderActions;
import io.mateu.ecdemo1.contenthub.application.visibility.ProjectionCache;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.ComponentTreeSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

import static io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui.esc;

/**
 * The intranet's home. A reader whose profile sees one of the configured home pages (Configuración →
 * Inicio) gets the first of them, drawn as a page of blocks; anyone else gets the default home: the
 * reader's pending confirmations, the notices and the latest news — all through the visibility
 * function, the lists cached per profile hash. One block of markup, drawn the same by both renderers.
 */
@Service
@Scope("prototype")
@Title("Intranet")
public class HomePage implements ComponentTreeSupplier {

    final PortalSupport portal;
    final ReaderActions actions;
    final ProjectionCache cache;
    final HomePages homes;
    final PageRenderer renderer;

    public HomePage(PortalSupport portal, ReaderActions actions, ProjectionCache cache, HomePages homes,
                    PageRenderer renderer) {
        this.portal = portal;
        this.actions = actions;
        this.cache = cache;
        this.homes = homes;
        this.renderer = renderer;
    }

    /** The first configured home page this reader can see, if any. */
    public Optional<VisibleItem> homeOf(Reader reader) {
        return homes.pageIds().stream().map(id -> portal.item(reader, id)).flatMap(Optional::stream)
                .filter(i -> ItemType.PAGE.name().equals(i.type())).findFirst();
    }

    @Override
    public Component component(HttpRequest httpRequest) {
        var reader = portal.reader(httpRequest);
        var home = homeOf(reader);
        if (home.isPresent()) {
            return Html.block(ItemPage.banner(reader.viewingAs()) + renderer.home(reader, home.get()));
        }
        var hash = reader.attributes().hash();
        var language = reader.attributes().language();
        var html = new StringBuilder(ItemPage.banner(reader.viewingAs()));
        html.append("<h1 style=\"margin:.25rem 0 1rem\">Hola, ").append(esc(reader.name())).append("</h1>");
        if (reader.viewingAs() == null) {
            var pending = actions.pendingConfirmations(reader);
            if (!pending.isEmpty()) {
                html.append(Ui.card("<h3 style=\"margin-top:0\">⚠️ Tienes " + pending.size()
                        + " contenido(s) pendiente(s) de confirmar</h3>" + PageRenderer.list(pending)));
            }
        }
        String notices = cache.get(hash, language, "home-notices", () ->
                PageRenderer.list(portal.latest(reader, new ContentQuery(Set.of(ItemType.NOTICE), null, null, null, 0, 5))));
        String news = cache.get(hash, language, "home-news", () ->
                PageRenderer.list(portal.latest(reader, new ContentQuery(Set.of(ItemType.ARTICLE, ItemType.PAGE), null,
                        null, null, 0, 8))));
        html.append("<div style=\"display:grid;grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:1rem\">")
                .append(Ui.card("<h3 style=\"margin-top:0\">📢 Avisos</h3>"
                        + (notices.contains("<li") ? notices : "<p style=\"opacity:.7\">Sin avisos.</p>")))
                .append(Ui.card("<h3 style=\"margin-top:0\">📰 Últimas noticias</h3>"
                        + (news.contains("<li") ? news : "<p style=\"opacity:.7\">Sin noticias.</p>")))
                .append("</div>");
        return Html.block(html.toString());
    }
}
