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
    final io.mateu.ecdemo1.contenthub.application.usecases.RemoteUis remoteUis;
    final io.mateu.ecdemo1.contenthub.infra.in.render.RemoteUiRenderer remote;

    public MenuEntryPage(SiteMenus menus, PortalSupport portal,
                         io.mateu.ecdemo1.contenthub.application.usecases.RemoteUis remoteUis,
                         io.mateu.ecdemo1.contenthub.infra.in.render.RemoteUiRenderer remote) {
        this.menus = menus;
        this.portal = portal;
        this.remoteUis = remoteUis;
        this.remote = remote;
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
                // a web component or an iframe, hosted in this page as a remote page draws it (<ec-ui-source>),
                // checked again at the door (RemoteUis.usable: active, scopes, audience, iframe origin)
                var use = io.mateu.ecdemo1.contenthub.domain.uisource.RemoteUi.of(r.source().id(),
                        r.route() == null || r.route().isBlank() ? null : r.route());
                var reader = portal.reader(httpRequest);
                yield Form.builder().title(entry.label()).content(List.of(remoteUis.usable(reader, use)
                        .map(s -> remote.component(s, use, io.mateu.ecdemo1.contenthub.infra.in.render.RemoteUiRenderer.Mode.PAGE))
                        .orElseGet(() -> EmptyState.builder().title("No puedes usar esta aplicación").build()))).build();
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
