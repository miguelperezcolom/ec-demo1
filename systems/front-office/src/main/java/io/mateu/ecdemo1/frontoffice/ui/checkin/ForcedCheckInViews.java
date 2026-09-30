package io.mateu.ecdemo1.frontoffice.ui.checkin;

import io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus;
import io.mateu.ecdemo1.frontoffice.ui.common.FrontOffice;
import io.mateu.uidl.data.Notice;
import io.mateu.uidl.data.VerticalLayout;
import io.mateu.uidl.fluent.Component;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * How a check-in with steps missing is drawn: in the wizard (what is missing, and that it can be forced
 * with a reason) and on the stay («Check-in incompleto», amber — red once the documents' deadline
 * passed — with «Completar»).
 */
public final class ForcedCheckInViews {

  static final DateTimeFormatter WHEN =
      DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.forLanguageTag("es")).withZone(ZoneId.of("Europe/Madrid"));

  private ForcedCheckInViews() {}

  /** Arriving with something missing: the desk can force it. */
  static boolean canForce(String stayId) {
    if (stayId == null) {
      return false;
    }
    var stay = FrontOffice.stayView(stayId).stay();
    return stay.status() == StayStatus.ARRIVING && !FrontOffice.checkInStatus(stayId).missing().isEmpty();
  }

  /** The wizard's notice of what the check-in lacks; nothing when it lacks nothing. */
  static Component pendingInWizard(String stayId) {
    if (stayId == null) {
      return new VerticalLayout();
    }
    var stay = FrontOffice.stayView(stayId).stay();
    var status = FrontOffice.checkInStatus(stayId);
    if (status.missing().isEmpty()) {
      return new VerticalLayout();
    }
    var arriving = stay.status() == StayStatus.ARRIVING;
    var text = arriving
        ? "Faltan pasos del check-in. «Confirmar check-in» no pasa sin ellos: complétalos, o fuerza el check-in "
            + "con un motivo (queda auditado). La estancia quedará «Check-in incompleto» y no podrá hacer el "
            + "check-out hasta completarlos."
        : "Completar el check-in: hazlo y pulsa «Confirmar check-in».";
    // what is missing in the text itself: Redwood draws a notice's buttons, not other content
    return Notice.builder()
        .theme(status.overdue() ? "danger" : "warning")
        .text("Falta: " + status.missingText() + ". " + text)
        .fullWidth(true)
        .style("margin: 0.75rem 0;")
        .build();
  }

  /** The stay's notice: «Check-in incompleto» with what is missing and «Completar»; nothing otherwise. */
  public static Component onTheStay(String stayId) {
    var status = FrontOffice.checkInStatus(stayId);
    if (!status.incomplete()) {
      return new VerticalLayout();
    }
    var forced = status.forced();
    var text = (status.overdue()
        ? "Check-in incompleto — PARTE DE VIAJEROS VENCIDO: pasaron " + FrontOffice.forcedDeadlineHours()
            + " h desde la llegada y falta documentación. Recepción ya está avisada."
        : "Check-in incompleto — forzado el " + WHEN.format(forced.forcedAt()) + " por " + forced.forcedBy()
            + ": «" + forced.reason() + "».")
        + (status.documentsMissing() && !status.overdue()
            ? " La documentación vence el " + WHEN.format(status.documentsDue()) + "." : "")
        + " Falta: " + status.missingText() + ". No puede hacer el check-out hasta completarlo.";
    // «Completar» as the notice's content: Redwood draws the buttons of a notice's content inside it,
    // not its own action (actionLabel), nor a list
    return Notice.builder()
        .id("checkin-incompleto")
        .theme(status.overdue() ? "danger" : "warning")
        .text(text)
        .fullWidth(true)
        .content(List.of(io.mateu.uidl.data.Button.builder().id("completar-checkin").label("Completar")
            .actionId("completarCheckin").build()))
        .build();
  }
}
