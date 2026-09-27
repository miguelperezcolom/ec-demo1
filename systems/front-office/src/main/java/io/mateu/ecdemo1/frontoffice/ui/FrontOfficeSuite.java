package io.mateu.ecdemo1.frontoffice.ui;

import io.mateu.ecdemo1.uicommons.user.UserWidget;
import io.mateu.uidl.annotations.App;
import io.mateu.uidl.annotations.AppContext;
import io.mateu.uidl.annotations.Audience;
import io.mateu.uidl.annotations.Menu;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.UI;
import io.mateu.uidl.data.RouteLink;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HomeRouteSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.WidgetSupplier;
import java.util.List;

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
   * Who is signed in, and a way out — the same widget as the consoles' shells (ui-commons'
   * UserWidget): a greeting that opens the email and Logout. Redwood draws it in the global
   * header's profile area.
   */
  @Override
  public List<Component> widgets(HttpRequest httpRequest) {
    return UserWidget.of(httpRequest);
  }
}
