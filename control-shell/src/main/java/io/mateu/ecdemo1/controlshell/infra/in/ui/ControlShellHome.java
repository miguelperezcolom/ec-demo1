package io.mateu.ecdemo1.controlshell.infra.in.ui;

import io.mateu.uidl.StyleConstants;
import io.mateu.uidl.annotations.FavIcon;
import io.mateu.uidl.annotations.KeycloakSecured;
import io.mateu.uidl.annotations.Logo;
import io.mateu.uidl.annotations.Menu;
import io.mateu.uidl.annotations.PageTitle;
import io.mateu.uidl.annotations.Style;
import io.mateu.uidl.annotations.UI;
import io.mateu.uidl.data.Anchor;
import io.mateu.uidl.data.HorizontalLayout;
import io.mateu.uidl.data.Popover;
import io.mateu.uidl.data.RemoteMenu;
import io.mateu.uidl.data.Text;
import io.mateu.uidl.data.VerticalLayout;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.WidgetSupplier;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static io.mateu.core.infra.JsonSerializer.fromJson;

/**
 * The control console — a second shell, on a host of its own.
 *
 * <p>Separate from the demo console rather than a menu inside it, and the separation is the point:
 * what lives behind here changes what the chat agent is and what it spends. Two hosts mean two
 * Keycloak clients, so a token minted for the demo console is not a token for this one, and the
 * gateway can require the {@code admin} realm role on this host without touching the other.
 *
 * <p>Like the demo shell, the Keycloak URL is compiled in — Mateu writes {@code @KeycloakSecured}
 * into the generated bootstrap page — so changing the hostname means rebuilding this image.
 */
@UI("")
@PageTitle("IA control plane")
@KeycloakSecured(url = "https://auth.ec1.mateu.io", realm = "ec-demo1", clientId = "control-plane")
@Logo("/images/riu.svg")
@FavIcon("/images/riu.svg")
// The catalogues are listings with long ids and long URLs in them; the default ~900px container
// wraps those into unreadable columns.
@Style(StyleConstants.FULL_WIDTH)
public class ControlShellHome implements WidgetSupplier {

    /** The IA catalogues, served by the control-plane pod, which labels the section "IA". */
    @Menu
    RemoteMenu controlPlane = new RemoteMenu("/_ia-cp");

    /**
     * User, group, role and permission management, served by the users pod, which labels the
     * section "Usuarios". It moved here from the demo console on purpose: administering who may
     * access the platform and what they may do is a control-plane concern, not part of using the
     * product. Both halves of the admin console — IA and Usuarios — now live behind the same
     * ai-admin gate on this host.
     */
    @Menu
    RemoteMenu users = new RemoteMenu("/_users");

    @Override
    public List<Component> widgets(HttpRequest httpRequest) {
        var widgets = new ArrayList<Component>();

        var authorization = httpRequest.getHeaderValue("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            // Anonymous: the bootstrap page is about to redirect to Keycloak.
            return widgets;
        }

        var claims = fromJson(new String(Base64.getUrlDecoder()
                .decode(authorization.substring("Bearer ".length()).split("\\.")[1])));

        widgets.add(HorizontalLayout.builder()
                .content(List.of(Popover.builder()
                        .wrapped(Text.builder()
                                .text("Hola, " + claims.get("name"))
                                .style("margin-right: 20px;")
                                .build())
                        .content(VerticalLayout.builder()
                                .content(List.of(
                                        new Text("Email: " + claims.get("email")),
                                        new Anchor("Logout", "javascript: window.logout();")))
                                .spacing(true)
                                .padding(true)
                                .build())
                        .build()))
                .style("align-items: flex-end;")
                .build());

        return widgets;
    }
}
