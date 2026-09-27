package io.mateu.ecdemo1.frontoffice.ui;

import io.mateu.uidl.annotations.App;
import io.mateu.uidl.annotations.AppContext;
import io.mateu.uidl.annotations.Audience;
import io.mateu.uidl.annotations.Menu;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.UI;
import io.mateu.uidl.data.Anchor;
import io.mateu.uidl.data.Popover;
import io.mateu.uidl.data.RouteLink;
import io.mateu.uidl.data.Text;
import io.mateu.uidl.data.VerticalLayout;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HomeRouteSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.WidgetSupplier;
import java.util.Base64;
import java.util.List;

import static io.mateu.core.infra.JsonSerializer.fromJson;

/**
 * The Front-Office Suite app shell: top menu with the four operational screens plus the two
 * application-context selectors — the persona switch ({@code Modo}, which drives every
 * {@code @Audience} projection because the field is named {@code audience}) and the active hotel.
 */
@UI("")
@Title("Front-Office Suite")
// The hotel's staff log in with the chain's Keycloak, as in the consoles (same realm and client).
@io.mateu.uidl.annotations.KeycloakSecured(url = "https://auth.ec1.mateu.io", realm = "ec-demo1", clientId = "demo")
@App(themeToggle = true) // variante AUTO: menú plano de RouteLinks → TABS (in-app navigation)
@io.mateu.uidl.annotations.Logo("/images/riu.svg")
@io.mateu.uidl.annotations.FavIcon("/images/riu.svg")
public class FrontOfficeSuite implements HomeRouteSupplier, WidgetSupplier {

  // la home es la welcome page (Bienvenida)
  @Override
  public String homeRoute() {
    return "/bienvenida";
  }

  public enum Modo {
    Staff,
    Cliente
  }

  public enum Hotel {
    PuntaCana,
    Bavaro,
    Aruba
  }

  // persona projection: naming this @AppContext field "audience" makes its value drive the
  // @Audience marks — unset → full view; Staff/Cliente → that audience's projection
  @AppContext(label = "Modo")
  Modo audience;

  @AppContext(label = "Hotel")
  Hotel hotel;

  @Menu
  RouteLink reservas =
      new RouteLink("/reservas", "Reservas").withIcon("vaadin:calendar-user");

  @Audience("Staff")
  @Menu
  RouteLink automatizaciones =
      new RouteLink("/automatizaciones", "Automatizaciones").withIcon("vaadin:tasks");

  /**
   * Who is signed in, and a way out — the same widget as the consoles' shells (ShellHome): a
   * greeting that opens the email and Logout. The identity is the Keycloak token the page sends
   * with every call (its claims, decoded — the resource server has already validated it);
   * {@code window.logout()} is defined by the bootstrap page Mateu writes for
   * {@code @KeycloakSecured}. Redwood draws it in the global header's profile area.
   */
  @Override
  public List<Component> widgets(HttpRequest httpRequest) {
    var authorization = httpRequest.getHeaderValue("Authorization");
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      // Anonymous: the bootstrap page is about to redirect to Keycloak — no one to greet yet.
      return List.of();
    }
    var claims =
        fromJson(
            new String(
                Base64.getUrlDecoder()
                    .decode(authorization.substring("Bearer ".length()).split("\\.")[1])));
    return List.of(
        Popover.builder()
            .wrapped(
                Text.builder()
                    // On a narrow screen the header keeps only an icon: the popover still has who and Logout.
                    .text(
                        "<vaadin-icon icon=\"vaadin:user\" style=\"display: var(--mateu-header-narrow-only, none); width: 1em; height: 1em; vertical-align: -0.125em;\"></vaadin-icon>"
                            + "<span style=\"display: var(--mateu-header-wide-only, inline)\">Hola, "
                            + claims.get("name")
                            + "</span>")
                    .style("margin-right: 20px; cursor: pointer;") // it opens the popover: who, and Logout
                    .build())
            .content(
                VerticalLayout.builder()
                    .content(
                        List.of(
                            new Text("Email: " + claims.get("email")),
                            new Anchor("Logout", "javascript: window.logout();")))
                    .spacing(true)
                    .padding(true)
                    .build())
            .build());
  }
}
