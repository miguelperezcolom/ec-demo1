package io.mateu.ecdemo1.frontoffice.ui.common;

import io.mateu.ecdemo1.frontoffice.infra.audit.AuditHistory;
import io.mateu.uidl.data.StatusItem;
import io.mateu.uidl.data.StatusList;
import io.mateu.uidl.data.Text;
import io.mateu.uidl.fluent.Component;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * «Historial»: who did what on the reservation, and when, newest first — the desk's operations and the
 * reception agent's, the notices read, and what the CRS did with its booking — as the audit service
 * keeps them. A status list, which both renderers draw; the audit trail's actions in the desk's words.
 */
public final class ReservationHistory {

  static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
  static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.forLanguageTag("es"));

  /** The audit trail's action names, as the desk says them. */
  static final Map<String, String> LABELS = Map.ofEntries(
      Map.entry("Check-in", "Check-in"),
      Map.entry("Check-out", "Check-out"),
      Map.entry("Room change", "Cambio de habitación"),
      Map.entry("Charge posted", "Cargo al folio"),
      Map.entry("Charge voided", "Cargo anulado"),
      Map.entry("Late check-out", "Late check-out"),
      Map.entry("Payment taken", "Cobro / preautorización"),
      Map.entry("Key encoded", "Llave / pulsera grabada"),
      Map.entry("Wifi created", "Tarjeta wifi"),
      Map.entry("Registration signed", "Registro firmado"),
      Map.entry("Add-on added", "Extra añadido"),
      Map.entry("Add-on removed", "Extra quitado"),
      Map.entry("Ancillaries chosen", "Ancillaries elegidos"),
      Map.entry("Ancillaries closed", "Ancillaries cerrados"),
      Map.entry("No show", "No-show"),
      Map.entry("Document scanned", "Documento escaneado"),
      Map.entry("Kardex edit", "Kárdex editado"),
      Map.entry("Contact updated", "Contacto actualizado"),
      Map.entry("Incident reported", "Incidencia abierta"),
      Map.entry("Incident resolved", "Incidencia resuelta"),
      Map.entry("Walk-in", "Walk-in"),
      Map.entry("Forced check-in", "Check-in forzado"),
      Map.entry("Forced check-in completed", "Check-in forzado completado"),
      Map.entry("Read check-in notices", "Avisos del check-in leídos"),
      Map.entry("Read check-out warnings", "Avisos de la salida confirmados"),
      Map.entry("Booking created", "Reserva creada en el CRS"),
      Map.entry("Booking modified", "Reserva modificada en el CRS"),
      Map.entry("Booking confirmed", "Reserva confirmada en el CRS"),
      Map.entry("Booking cancelled", "Reserva cancelada en el CRS"),
      Map.entry("Booking no-show", "No-show registrado en el CRS"),
      Map.entry("Payment registered", "Cobro registrado en el CRS"));

  private ReservationHistory() {}

  public static Component of(String stayId) {
    var history = AuditHistory.of(stayId, FrontOffice.locator(stayId));
    if (history.isEmpty()) {
      return Text.builder().text("El historial no está disponible ahora.").noMargins(true).build();
    }
    if (history.get().isEmpty()) {
      return Text.builder().text("Nadie ha hecho nada todavía con esta reserva.").noMargins(true).build();
    }
    var items = history.get().stream().map(ReservationHistory::item).toList();
    return StatusList.builder().compact(true).frameless(true).style("width: 100%;").items(items).build();
  }

  static StatusItem item(AuditHistory.Entry e) {
    var when = e.at() == null ? "" : WHEN.format(e.at().atZone(ZONE)) + " · ";
    var where = "booking".equals(e.service()) ? " · CRS" : "";
    return StatusItem.builder()
        .id("hist-" + (e.at() == null ? "" : e.at().toEpochMilli()) + "-" + Math.abs((e.action() + e.by()).hashCode()))
        .title(label(e.action()))
        .description(when + (e.by() == null ? "—" : e.by()) + where
            + (e.response() == null || e.response().isBlank() || "OK".equals(e.response()) ? "" : " — " + e.response()))
        .status(e.succeeded() ? "Hecho" : "No hecho")
        .statusColor(e.succeeded() ? "success" : "danger")
        .build();
  }

  /** The action in the desk's words; a refusal as what was refused. */
  static String label(String action) {
    if (action == null) {
      return "—";
    }
    var known = LABELS.get(action);
    if (known != null) {
      return known;
    }
    var refused = action.indexOf(" refused");
    if (refused > 0) {
      return LABELS.getOrDefault(action.substring(0, refused), action.substring(0, refused)) + " rechazado";
    }
    return action;
  }
}
