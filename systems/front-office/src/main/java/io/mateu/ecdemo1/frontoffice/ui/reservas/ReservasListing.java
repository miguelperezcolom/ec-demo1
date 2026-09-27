package io.mateu.ecdemo1.frontoffice.ui.reservas;

import io.mateu.ecdemo1.frontoffice.ui.Paging;
import io.mateu.ecdemo1.frontoffice.domain.stay.Stay;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayReadModel.StayRow;
import io.mateu.ecdemo1.frontoffice.application.DemoReservationsService;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayReadModel;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.ui.common.Tiers;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.Trigger;
import io.mateu.uidl.annotations.TriggerType;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.interfaces.Filterable;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import io.mateu.uidl.interfaces.Searchable;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Reservas como LISTADO simple (un crud, no un collection-detail): tabla con buscador de todas las
 * estancias, cada línea con su estado — "Llega hoy/mañana/&lt;fecha&gt;", "Sale
 * hoy/mañana/&lt;fecha&gt;", "Salió &lt;fecha&gt;". El clic de fila abre la reserva como PÁGINA
 * aparte según el estado: el wizard de check-in, el 360 de en casa o el folio de check-out (para
 * las in house, el check-out se lanza desde el toolbar del 360).
 */
@Title("Reservas")
@Trigger(type = TriggerType.OnLoad, actionId = "search")
// tras seedear reservas de demo, el propio listado se refresca (bus estándar)
@Trigger(type = TriggerType.OnCustomEvent, actionId = "search", eventName = "reservas-seeded")
@org.springframework.stereotype.Service
@org.springframework.context.annotation.Scope("prototype")
public class ReservasListing
    implements Listing<ReservasListing.Reserva>, Searchable, Filterable<ReservasListing.Filtros> {

  final StayReadModel stayReads;
  final StayRepository stays;
  final DemoReservationsService demoReservations;

  public ReservasListing(StayReadModel stayReads, StayRepository stays, DemoReservationsService demoReservations) {
    this.stayReads = stayReads;
    this.stays = stays;
    this.demoReservations = demoReservations;
  }

  private static final DateTimeFormatter FECHA =
      DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"));

  /** Selector rápido del listado (chips junto al smart search): un filtro de ENUM —
   *  cada valor es una vista operativa del día. */
  public enum Vista {
    @Label("Llegadas hoy")
    LLEGADAS_HOY,
    @Label("Salidas hoy")
    SALIDAS_HOY,
    @Label("In house")
    IN_HOUSE
  }

  public static class Filtros {
    @Label("Vista")
    Vista vista;
  }

  public record Reserva(
      String id,
      @Label("Huésped") String huesped,
      @Label("Habitación") String habitacion,
      @Label("Noches") long noches,
      @Label("Estado") String estado,
      @Label("Tier") Status tier) {}

  @Override
  public ListingData<Reserva> search(SearchRequest request, HttpRequest httpRequest) {
    var searchText = request.searchText();
    var filtros = filters(request);
    // Una consulta (estancia + huésped, sin colecciones); el filtro, el orden y la búsqueda sobre la
    // fila ya pintada siguen en memoria, que es lo que permite buscar por "Llega mañana".
    var rows =
        stayReads.rows().stream()
            .filter(s -> matchesVista(s, filtros == null ? null : filtros.vista))
            .sorted(
                java.util.Comparator.comparing((StayRow s) -> s.status().ordinal())
                    .thenComparing(
                        s ->
                            s.status() == io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus.ARRIVING
                                ? s.checkIn()
                                : s.checkOut()))
            .map(this::row)
            .filter(row -> matches(row, searchText))
            .toList();
    return Paging.page(rows, request);
  }

  /** El selector rápido: llegadas de hoy / salidas de hoy / en casa. */
  private static boolean matchesVista(StayRow stay, Vista vista) {
    if (vista == null) {
      return true;
    }
    var today = LocalDate.now();
    return switch (vista) {
      case LLEGADAS_HOY ->
          stay.status() == io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus.ARRIVING
              && !stay.checkIn().isAfter(today);
      case SALIDAS_HOY ->
          (stay.status() == io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus.IN_HOUSE
              || stay.status() == io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus.DEPARTED)
              && stay.checkOut().isEqual(today);
      case IN_HOUSE ->
          stay.status() == io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus.IN_HOUSE;
    };
  }

  private Reserva row(StayRow stay) {
    var habitacion = stay.roomNumber() == null || stay.roomNumber().isBlank()
        ? "Sin asignar" : "Hab " + stay.roomNumber();
    return new Reserva(
        stay.id(),
        stay.guestName(),
        habitacion + " · " + stay.roomType(),
        java.time.temporal.ChronoUnit.DAYS.between(stay.checkIn(), stay.checkOut()),
        estadoLabel(stay.status(), stay.checkIn(), stay.checkOut()),
        Tiers.badge(stay.guestTier()));
  }

  private boolean matches(Reserva row, String searchText) {
    if (searchText == null || searchText.isBlank()) {
      return true;
    }
    // The locator too: it is what the desk reads off a voucher or a call.
    var hay = (row.id() + " " + row.huesped() + " " + row.habitacion() + " " + row.estado() + " " + (row.tier() == null ? "" : row.tier().message()))
        .toLowerCase();
    for (var word : searchText.trim().toLowerCase().split("\\s+")) {
      if (!hay.contains(word)) {
        return false;
      }
    }
    return true;
  }

  static String estadoLabel(Stay stay) {
    return estadoLabel(stay.status(), stay.checkIn(), stay.checkOut());
  }

  static String estadoLabel(
      io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus status, LocalDate checkIn, LocalDate checkOut) {
    var today = LocalDate.now();
    return switch (status) {
      case ARRIVING -> relativo("Llega", checkIn, today);
      case IN_HOUSE -> relativo("Sale", checkOut, today);
      case DEPARTED -> "Salió " + FECHA.format(checkOut);
      case CANCELLED -> "Cancelada · llegaba " + FECHA.format(checkIn);
      case NO_SHOW -> "No show · llegaba " + FECHA.format(checkIn);
    };
  }

  private static String relativo(String verbo, LocalDate fecha, LocalDate today) {
    if (fecha.isEqual(today)) {
      return verbo + " hoy";
    }
    if (fecha.isEqual(today.plusDays(1))) {
      return verbo + " mañana";
    }
    return verbo + " " + FECHA.format(fecha);
  }

  // ── el toolbar del listado: primero la acción de recepción (el walk-in), después la ayuda de
  // la demo — Redwood despacha ya las acciones secundarias, así que el orden es solo el de uso ──

  /** Un cliente sin reserva en el mostrador: el CRS pone el precio y hace la reserva. */
  @io.mateu.uidl.annotations.ListToolbarButton(rowsSelectedRequired = false)
  @io.mateu.uidl.annotations.Toolbar(order = 1)
  @Label("＋ Walk-in")
  public void walkIn() {
    // la lógica vive en handleAction (dispatch uniforme con "view")
  }

  /** Crea ~10 reservas de demo repartidas entre llegadas, en casa y salidas. */
  @io.mateu.uidl.annotations.ListToolbarButton(rowsSelectedRequired = false)
  @io.mateu.uidl.annotations.Toolbar(order = 2)
  @Label("＋ 10 reservas demo")
  public void seedDemo() {
    // la lógica vive en handleAction (dispatch uniforme con "view")
  }

  // ── clic de fila: abrir la reserva como página según su estado ───────────────

  @Override
  public boolean supportsAction(String actionId) {
    return "view".equals(actionId) || "seedDemo".equals(actionId) || "walkIn".equals(actionId)
        || Listing.super.supportsAction(actionId);
  }

  @Override
  public Object handleAction(String actionId, HttpRequest httpRequest) {
    if ("seedDemo".equals(actionId)) {
      return List.of(
          new io.mateu.uidl.data.Message(demoReservations.seed() + " reservas de demo creadas"),
          io.mateu.uidl.data.UICommand.dispatchEvent("reservas-seeded"));
    }
    if ("walkIn".equals(actionId)) {
      return URI.create("/walk-in");
    }
    if ("view".equals(actionId)) {
      var id = String.valueOf(httpRequest.runActionRq().parameters().get("id"));
      var stay = stays.findById(id).orElse(null);
      if (stay == null) {
        return null;
      }
      // ruta ÚNICA: la Reserva 360 muestra el estado y ofrece las acciones que tocan
      // (anidada bajo /reservas para que la pestaña Reservas siga marcada)
      return URI.create("/reservas/" + id);
    }
    return Listing.super.handleAction(actionId, httpRequest);
  }
}
