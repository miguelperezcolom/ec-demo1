package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.menu.MenuLink;
import io.mateu.ecdemo1.contenthub.application.menu.SiteMenus;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.data.Anchor;
import io.mateu.uidl.data.EmptyState;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.fluent.Form;
import io.mateu.uidl.interfaces.ComponentTreeSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * What a menu entry opens when Mateu's menu cannot open it itself — /intranet/enlace/{site}/{entry} for
 * an external URL (a menu entry navigates inside the app, so the reader gets the link, opening in a new
 * tab) and /intranet/ui/{site}/{entry} for another UI that is not a Mateu app (hosted in a frame,
 * embed.js). The entry is looked up again as this reader gets it: whoever is not shown it is refused
 * here too, so a typed URL opens nothing a menu would hide.
 */
@Service
@Scope("prototype")
public class MenuEntryPage implements ComponentTreeSupplier, Identifiable {

    String id;

    final SiteMenus menus;
    final PortalSupport portal;

    public MenuEntryPage(SiteMenus menus, PortalSupport portal) {
        this.menus = menus;
        this.portal = portal;
    }

    @Override
    public Component component(HttpRequest httpRequest) {
        id = httpRequest.lastPathItem();
        var siteId = httpRequest.penultimatePathItem();
        var context = PortalMenu.get().context(siteId, httpRequest);
        var entry = menus.shownEntry(context, id).orElse(null);
        if (entry == null || entry.link() == null) {
            return Form.builder().title("Menú").content(List.of(
                    EmptyState.builder().title("Este enlace no está disponible para ti").build())).build();
        }
        return switch (entry.link()) {
            case MenuLink.External e -> Form.builder().title(entry.label()).content(List.of(
                    PortalComponents.small("Este enlace sale de la intranet: se abre en una pestaña nueva."),
                    new Anchor(entry.label() + " ↗", e.url(), "_blank", "", ""))).build();
            case MenuLink.Remote r -> {
                var url = r.source().basePath() + r.route();
                yield Form.builder().title(entry.label()).content(List.of(Html.block(
                        "<div data-ec-embed=\"" + Html.escape(url) + "\" data-ec-embed-kind=\"ui\" "
                                + "style=\"width:100%;height:80vh\"><a href=\"" + Html.escape(url)
                                + "\" target=\"_blank\" rel=\"noopener\">" + Html.escape(r.source().name())
                                + " ↗</a></div>"))).build();
            }
            default -> Form.builder().title(entry.label()).content(List.of(
                    EmptyState.builder().title("Esta entrada se abre desde el menú").build())).build();
        };
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return "Menú";
    }
}
