package io.mateu.ecdemo1.frontoffice.ui.reservas;

import io.mateu.ecdemo1.frontoffice.domain.folio.Folio;
import io.mateu.ecdemo1.frontoffice.domain.stay.Incident;
import io.mateu.ecdemo1.frontoffice.domain.stay.IncidentStatus;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.ui.common.GuestHeaders;
import io.mateu.uidl.data.FieldDataType;
import io.mateu.uidl.data.FormField;
import io.mateu.uidl.data.Ledger;
import io.mateu.uidl.data.LedgerLine;
import io.mateu.uidl.data.Meter;
import io.mateu.uidl.data.Notice;
import io.mateu.uidl.data.PaymentMethod;
import io.mateu.uidl.data.PaymentPicker;
import io.mateu.uidl.data.StatusItem;
import io.mateu.uidl.data.StatusList;
import io.mateu.uidl.data.Text;
import io.mateu.uidl.data.TextContainer;
import io.mateu.uidl.data.VerticalLayout;
import io.mateu.uidl.fluent.Component;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Reserva 360's money and aftermath: the stay in the house (balance and incidents), the check-out
 * mode (folio, charges, payment) and a closed stay (gone, no-show or cancelled).
 */
final class EstanciaPanel {

  static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"));

  private static final DateTimeFormatter FECHA_HORA =
      DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.forLanguageTag("es"));

  private final ReservaOverview r;

  EstanciaPanel(ReservaOverview r) {
    this.r = r;
  }

  /** El balance del folio para el header del panel Estancia: "€ 1.710,50 · 95% preaut.". */
  String balanceResumen() {
    var folio = r.view().folio();
    if (folio == null) {
      return "sin cargos";
    }
    var balance = folio.balance().doubleValue();
    var preauth = folio.preauthorized() == null
        ? Math.max(balance, 1) : folio.preauthorized().doubleValue();
    return GuestHeaders.euros(balance) + " · " + Math.round(balance / preauth * 100) + "% preaut.";
  }

  /** ¿El folio ya lleva el late check-out? */
  static boolean lateCheckout(Folio folio) {
    return folio != null && folio.lateCheckOutContracted();
  }

  /** La salida: el día y la hora — las 15:00 con late check-out contratado. */
  static String salida(Stay stay, Folio folio) {
    return DAY.format(stay.checkOut()) + " · " + (lateCheckout(folio) ? "15:00 (late check-out)" : "12:00");
  }

  Component paraInHouse(Stay stay) {
    if (r.modoCheckout) {
      return new VerticalLayout(); // en modo check-out mandan el folio y el cobro
    }
    var folio = r.view().folio();
    var balance = folio == null ? 0d : folio.balance().doubleValue();
    var preauth = folio == null || folio.preauthorized() == null
        ? Math.max(balance, 1) : folio.preauthorized().doubleValue();
    var pct = (int) Math.round(balance / preauth * 100);
    var content = new ArrayList<Component>();
    // el KPI del balance, tal cual
    content.add(
        Meter.builder()
            .label("BALANCE ACTUAL")
            .value(balance)
            .max(preauth)
            .unit("€")
            .caption(pct + "% de la preautorización consumido · "
                + (folio == null ? 0 : folio.lines().size()) + " cargos")
            .warnAt(preauth * 0.8)
            .dangerAt(preauth * 0.95)
            .build());
    // incidencias: TODAS con la misma ficha (titulo + badge de estado a la derecha) y su
    // CRONOLOGIA debajo (fecha/hora - comentario, abriendo con la descripcion); las
    // resueltas al final del listado
    content.add(Text.builder().text("Incidencias (" + stay.incidents().size() + ")")
        .container(TextContainer.h3).style("margin: 0;").build());
    var incidencias = new ArrayList<>(stay.incidents().stream()
        .filter(i -> i.status() != IncidentStatus.RESOLVED).toList());
    incidencias.addAll(stay.incidents().stream()
        .filter(i -> i.status() == IncidentStatus.RESOLVED).toList());
    if (incidencias.isEmpty()) {
      content.add(Notice.builder()
          .theme("success")
          .text("Sin incidencias en la habitación")
          .fullWidth(true)
          .build());
    } else {
      content.add(StatusList.builder()
          .compact(true).frameless(true)
          .itemHeadingLevel(4)
          .style("width: 100%;")
          .items(incidencias.stream()
              .map(i -> StatusItem.builder()
                  .id("inc-" + i.code())
                  .title(i.title())
                  .description(i.type() != null ? i.type().label() : null)
                  .status(switch (i.status()) {
                    case RESOLVED -> "✓ Resuelta";
                    case IN_PROGRESS -> "En curso";
                    default -> "Abierta";
                  })
                  .statusColor(switch (i.status()) {
                    case RESOLVED -> "success";
                    case IN_PROGRESS -> "warning";
                    default -> "error";
                  })
                  .lines(cronologia(i))
                  .actionLabel(i.status() == IncidentStatus.RESOLVED ? null : "Resolver")
                  .actionId(i.status() == IncidentStatus.RESOLVED ? null : "resolverIncidencia")
                  .actionIcon(i.status() == IncidentStatus.RESOLVED ? null : "vaadin:check")
                  .build())
              .toList())
          .build());
    }
    return VerticalLayout.builder().content(content).style("width: 100%; gap: 1rem;").build();
  }

  /** La cronologia de una incidencia: apertura (descripcion), curso y resolucion. */
  static List<String> cronologia(Incident i) {
    var lineas = new ArrayList<String>();
    if (i.openedAt() != null) {
      lineas.add(FECHA_HORA.format(i.openedAt()) + " — " + i.description());
    } else {
      lineas.add(i.description());
    }
    if (i.status() == IncidentStatus.IN_PROGRESS && i.openedAt() != null) {
      lineas.add(FECHA_HORA.format(i.openedAt().plusMinutes(35))
          + " — Mantenimiento avisado · en curso");
    }
    if (i.status() == IncidentStatus.RESOLVED && i.resolvedAt() != null) {
      lineas.add(FECHA_HORA.format(i.resolvedAt()) + " — Resuelta por recepción");
    }
    return lineas;
  }

  /** Cómo acabó una estancia cerrada, para el título: salió, no se presentó o se canceló. */
  static String cierre(Stay stay) {
    return switch (stay.status()) {
      case NO_SHOW -> "no show el " + DAY.format(stay.checkIn());
      case CANCELLED -> "cancelada";
      default -> "salió el " + DAY.format(stay.checkOut());
    };
  }

  /** El aviso de una estancia cerrada: en un no show, que no llegó nadie y lo que cuesta. */
  static String avisoCierre(Stay stay) {
    return switch (stay.status()) {
      case NO_SHOW -> "No se presentó nadie: el CRS la canceló como no show"
          + (stay.total() == null ? "" : ", con un cargo de " + euros(stay.total()));
      case CANCELLED -> "Reserva cancelada antes de la llegada";
      default -> "Salió el " + DAY.format(stay.checkOut()) + " — folio cerrado";
    };
  }

  static String euros(java.math.BigDecimal amount) {
    return String.format(Locale.forLanguageTag("es-ES"), "%,.2f €", amount);
  }

  Component paraSalida(Stay stay) {
    var folio = r.view().folio();
    var content = new ArrayList<Component>();
    content.add(Notice.builder().theme("info").text(avisoCierre(stay)).build());
    if (folio != null) {
      content.add(ledger(folio).build());
    }
    return VerticalLayout.builder().content(content).style("width: 100%; gap: 1rem;").build();
  }

  /** El desglose de un folio como Ledger (sin folio, vacío). */
  static Ledger.LedgerBuilder ledger(Folio folio) {
    return Ledger.builder()
        .currency("€")
        .totalLabel("Total")
        .lines(folio == null ? List.of() : folio.lines().stream()
            .map(l -> LedgerLine.builder()
                .concept(l.concept())
                .amount(l.amount() == null ? null : l.amount().doubleValue())
                .included(l.included())
                .includedLabel(l.includedLabel())
                .build())
            .toList())
        .total(folio == null ? 0 : folio.balance().doubleValue());
  }

  // ── modo CHECK-OUT ───────────────────────────────────────────────────────────

  /** Modo check-out: la info clave de la zona complementaria — la salida y los huéspedes
   *  (el balance y el preautorizado llegan como facts del EntityHeader del huésped). */
  Component claveCheckout(Stay stay) {
    var view = r.view();
    var contenido = new ArrayList<Component>();
    contenido.add(Text.builder().text("Salida").container(TextContainer.h3).style("margin: 0;").build());
    contenido.add(Text.builder().text(salida(stay, view.folio())).noMargins(true).build());
    contenido.add(Text.builder().text(stay.nights() + " noches · " + stay.board())
        .size(io.mateu.uidl.data.TextSize.xs).noMargins(true).build());
    contenido.add(Text.builder().text("Huéspedes")
        .container(TextContainer.h3).style("margin: 1.5rem 0 0;").build());
    contenido.add(Text.builder().text(view.guest().name()).noMargins(true).build());
    for (var companion : stay.companions()) {
      contenido.add(Text.builder().text(companion.name()).noMargins(true).build());
    }
    return VerticalLayout.builder().style("width: 100%; gap: .25rem;").content(contenido).build();
  }

  /** Modo check-out: el desglose del folio (vacío fuera del modo). */
  Component checkoutFolioPanel() {
    if (!r.modoCheckout) {
      return new VerticalLayout();
    }
    return VerticalLayout.builder()
        .style("width: 100%; gap: .5rem;")
        .content(List.of(
            Text.builder().text("Desglose folio").container(TextContainer.h3).style("margin: 0;").build(),
            ledger(r.view().folio()).style("width: 100%;").build()))
        .build();
  }

  /** Modo check-out: posteo de cargos (vacío fuera del modo). */
  Component checkoutCargosPanel() {
    if (!r.modoCheckout) {
      return new VerticalLayout();
    }
    var content = new ArrayList<Component>();
    content.add(Text.builder().text("Postear cargo").container(TextContainer.h3).style("margin: 0;").build());
    content.add(FormField.builder()
        .id("cargoBusqueda")
        .label("Buscar por nombre o código")
        .dataType(FieldDataType.string)
        .style("width: 100%; max-width: 32rem;")
        .build());
    var cargoBusqueda = r.cargoBusqueda;
    if (cargoBusqueda != null && !cargoBusqueda.isBlank()) {
      var busca = cargoBusqueda.trim().toLowerCase();
      var matches = r.chargeCatalog.findAll().stream()
          .filter(item -> item.name().toLowerCase().contains(busca)
              || item.code().toLowerCase().contains(busca))
          .toList();
      content.add(matches.isEmpty()
          ? Notice.builder().theme("warning")
              .text("Sin coincidencias para \"" + cargoBusqueda + "\"").slim(true)
              .fullWidth(true).build()
          : StatusList.builder()
              .rowActionId("seleccionarCargo")
              .compact(true)
              .style("width: 100%;")
              .items(matches.stream()
                  .map(item -> StatusItem.builder()
                      .id(item.code()).title(item.name()).description(item.code())
                      .status(GuestHeaders.euros(item.price())).statusColor("contrast")
                      .build())
                  .toList())
              .build());
    }
    return VerticalLayout.builder().style("width: 100%; gap: .5rem;").content(content).build();
  }

  /** Modo check-out: el cobro (vacío fuera del modo). */
  Component checkoutCobroPanel() {
    if (!r.modoCheckout) {
      return new VerticalLayout();
    }
    var f = r.view().folio();
    return VerticalLayout.builder()
        .style("width: 100%; gap: .5rem;")
        .content(List.of(
            Text.builder().text("Cobro").container(TextContainer.h3).style("margin: 0;").build(),
            PaymentPicker.builder()
                .actionId("confirmPayment")
                .methodActionId("cambiarMetodo")
                .methods(List.of(
                    PaymentMethod.builder().id("card").label("Tarjeta").build(),
                    PaymentMethod.builder().id("cash").label("Efectivo").build(),
                    PaymentMethod.builder().id("points").label("Puntos").build()))
                .selected(r.metodoPago)
                .contextLabel("PREAUTORIZADO")
                .contextValue(GuestHeaders.euros(f == null ? null : f.preauthorized()))
                .confirmLabel("Confirmar — " + GuestHeaders.euros(GuestHeaders.balance(f)))
                .build()))
        .build();
  }
}
