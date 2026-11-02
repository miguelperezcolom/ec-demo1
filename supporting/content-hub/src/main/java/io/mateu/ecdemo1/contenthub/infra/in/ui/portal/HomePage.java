package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.out.ContentQuery;
import io.mateu.ecdemo1.contenthub.application.out.HomePages;
import io.mateu.ecdemo1.contenthub.application.out.VisibleItem;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.ReaderActions;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.Button;
import io.mateu.uidl.data.ButtonStyle;
import io.mateu.uidl.data.DashboardLayout;
import io.mateu.uidl.data.RouteLink;
import io.mateu.uidl.data.WelcomePage;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.fluent.Form;
import io.mateu.uidl.interfaces.ComponentTreeSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PageRenderer.panel;
import static io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PortalComponents.items;
import static io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PortalComponents.withLink;

/**
 * The intranet's home: Mateu's welcome page template (Redwood draws Spectra's welcome page) — a
 * banner greeting the reader, and tiles under it. A reader whose profile sees one of the configured
 * home pages (Configuración → Inicio) gets the first of them, its blocks as the tiles; anyone else
 * gets the default home: their pending confirmations, the notices and the latest news — all through
 * the visibility function.
 */
@Service
@Scope("prototype")
@Title("Intranet")
public class HomePage implements ComponentTreeSupplier {

    static final DateTimeFormatter TODAY = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.of("es"));

    final PortalSupport portal;
    final ReaderActions actions;
    final HomePages homes;
    final PageRenderer renderer;

    public HomePage(PortalSupport portal, ReaderActions actions, HomePages homes, PageRenderer renderer) {
        this.portal = portal;
        this.actions = actions;
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
        var today = TODAY.format(LocalDate.now(Ui.ZONE));
        var home = homeOf(reader).map(page -> renderer.home(reader, page))
                .orElseGet(() -> defaultHome(reader));
        var overline = home.overline() == null ? "Intranet" : home.overline();
        if (reader.viewingAs() != null) {
            overline = "Vista previa como «" + reader.viewingAs() + "»";
        }
        return Form.builder()
                .title("Hola, " + reader.name())
                .overline(overline)
                .subtitle(home.subtitle() == null ? Character.toUpperCase(today.charAt(0)) + today.substring(1)
                        : home.subtitle())
                .template(new WelcomePage(PortalComponents.TONE.name()))
                .toolbarItem(Button.builder().label("Buscar en la intranet").buttonStyle(ButtonStyle.primary)
                        .actionable(new RouteLink(Ui.PORTAL + "/buscar")).build())
                .toolbarItem(Button.builder().label("Nueva solicitud")
                        .actionable(new RouteLink(Ui.PORTAL + "/nuevaSolicitud")).build())
                .content(List.of(DashboardLayout.builder().id("tiles").columns(3).items(home.panels()).build()))
                .build();
    }

    PageRenderer.Home defaultHome(Reader reader) {
        var panels = new ArrayList<Component>();
        if (reader.viewingAs() == null) {
            var pending = actions.pendingConfirmations(reader);
            if (!pending.isEmpty()) {
                panels.add(panel("pendientes", "Pendiente de confirmar (" + pending.size() + ")",
                        withLink(items(pending, ""), "Ver lo pendiente", Ui.PORTAL + "/pendientes"), 3));
            }
        }
        panels.add(panel("avisos", "Avisos", withLink(items(portal.latest(reader,
                new ContentQuery(Set.of(ItemType.NOTICE), null, null, null, 0, 5)), "Sin avisos."),
                "Todos los avisos", Ui.PORTAL + "/avisos"), 1));
        panels.add(panel("noticias", "Últimas noticias", withLink(items(portal.latest(reader,
                new ContentQuery(Set.of(ItemType.ARTICLE, ItemType.PAGE), null, null, null, 0, 8)), "Sin noticias."),
                "Todas las noticias", Ui.PORTAL + "/noticias"), 2));
        return new PageRenderer.Home(null, null, panels);
    }
}
