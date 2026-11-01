package io.mateu.ecdemo1.intranetshell.infra.in.ui;

import io.mateu.ecdemo1.uicommons.user.UserWidget;
import io.mateu.uidl.annotations.App;
import io.mateu.uidl.annotations.FavIcon;
import io.mateu.uidl.annotations.KeycloakSecured;
import io.mateu.uidl.annotations.Logo;
import io.mateu.uidl.annotations.Menu;
import io.mateu.uidl.annotations.PageTitle;
import io.mateu.uidl.annotations.Script;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.UI;
import io.mateu.uidl.data.RemoteMenu;
import io.mateu.uidl.fluent.AppVariant;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HomeRouteSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.WidgetSupplier;
import java.util.List;

/**
 * The intranet on a host of its own (intranet.ec1.mateu.io, and rw-intranet for Redwood), beside the
 * data plane's "Intranet" entry: employees who only read the intranet get it without the business
 * consoles around it. One remote, content-hub's /_portal — the reader portal alone, without the
 * editorial backoffice (editors work on the data plane).
 *
 * <p>Like consoles/shell: one module, two images (-Predwood), the Keycloak URL compiled in, the demo
 * client (its redirect URIs list both hosts).
 */
@UI("")
@Title("Intranet")
@PageTitle("Intranet" + Renderer.TITLE_SUFFIX)
@KeycloakSecured(url = "https://auth.ec1.mateu.io", realm = "ec-demo1", clientId = "demo")
// The portal's embeds: YouTube/Vimeo/Drive players and PDFs drawn as placeholders (the renderers'
// sanitiser drops iframes). content-hub serves it; public at the gateway on this host too.
@Script(src = "/_intranet/embed.js")
@Logo("/images/riu.svg")
@FavIcon("/images/riu.svg")
// The section (the portal) in the hamburger and its entries in the band under the header, as in the
// consoles — here there is just the one section.
@App(value = AppVariant.HAMBURGER_SECTIONS, themeToggle = true, accentColor = "#D2232A", accentStripSeed = 11)
public class IntranetShellHome implements WidgetSupplier, HomeRouteSupplier {

    /** The portal's home. A load of the bare host is redirected there first: see HomeRedirect. */
    @Override
    public String homeRoute() {
        return "/intranet/inicio";
    }

    /** The portal: news, notices, documents, sections, search, favourites and confirmations. */
    @Menu
    RemoteMenu portal = new RemoteMenu("/_portal").withLabel("Intranet");

    /** Who is signed in. No inbox badge: the inbox is the consoles', not the intranet's. */
    @Override
    public List<Component> widgets(HttpRequest httpRequest) {
        return UserWidget.of(httpRequest);
    }
}
