package io.mateu.ecdemo1.frontoffice.ui;

import io.mateu.ecdemo1.uicommons.user.UserWidget;
import io.mateu.uidl.annotations.AI;
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
 * {@code @Audience} projection because the field is named {@code audience}) and the active hotel —
 * and, in the header, the reception agent's chat.
 */
@UI("")
@Title("Front-Office Suite")
// The hotel's staff log in with the chain's Keycloak, as in the consoles (same realm and client).
@io.mateu.uidl.annotations.KeycloakSecured(url = "https://auth.ec1.mateu.io", realm = "ec-demo1", clientId = "demo")
@App(themeToggle = true) // variante AUTO: menú plano de RouteLinks → TABS (in-app navigation)
@io.mateu.uidl.annotations.Logo("/images/riu.svg")
@io.mateu.uidl.annotations.FavIcon("/images/riu.svg")
// Web Push at the desk: the inbox's script, through the gateway (front.ec1's /_inbox/push/**). It
// offers "Activar avisos" once, and the user menu says and switches the state. What reaches the desk
// is what the recipients push to it (FRONT_DESK_PUSH) — nothing, until one does.
@io.mateu.uidl.annotations.Script(src = "/_inbox/push/push.js")
// The reception agent's chat. Declaring it is what puts Redwood's conversation button in the global
// header (next to the user widget) and opens the chat in the drawer on the left. Mateu's client posts
// the prompt here with the session's token; the gateway sends front.ec1's /ai/** to the one ia-agent,
// stamping the front-office channel and the reception agent as its default — with the front office's
// MCP tools and nothing else.
@AI(sse = "/ai/api/agent/stream")
// Sin migas automáticas: el front office tiene pocas entradas de menú y su Reserva 360 ya dice dónde
// está el recepcionista; un «ir al padre» añadido en su cabecera sólo le quitaría sitio.
@io.mateu.uidl.annotations.NoBreadcrumbs
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

  // persona projection: naming this @AppContext field "audience" makes its value drive the
  // @Audience marks — unset → full view; Staff/Cliente → that audience's projection
  @AppContext(label = "Modo")
  Modo audience;

  // the hotels this front office serves (Hotels: its configured property, MRU01 ↔ Opera XMAR) — the
  // choice travels in every request (HttpRequest.appContext("hotel")) and scopes the stays shown
  @AppContext(label = "Hotel")
  io.mateu.ecdemo1.frontoffice.ui.common.Hotels hotel;

  @Menu
  RouteLink reservas =
      new RouteLink("/reservas", "Reservas").withIcon("vaadin:calendar-user");

  // Atajos a las tres vistas del día de recepción: el mismo listado de reservas con su vista
  // (ReservasListing.Vista) ya elegida, como hacen las tarjetas de la bienvenida.
  @Audience("Staff")
  @Menu
  RouteLink llegadas =
      new RouteLink("/reservas?vista=LLEGADAS_HOY", "Llegadas").withIcon("vaadin:sign-in");

  @Audience("Staff")
  @Menu
  RouteLink inHouse =
      new RouteLink("/reservas?vista=IN_HOUSE", "In house").withIcon("vaadin:home");

  @Audience("Staff")
  @Menu
  RouteLink salidas =
      new RouteLink("/reservas?vista=SALIDAS_HOY", "Salidas").withIcon("vaadin:sign-out");

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
