package io.mateu.ecdemo1.frontoffice.ui.checkin;

import io.mateu.ecdemo1.frontoffice.application.IncompleteCheckIns;
import io.mateu.ecdemo1.frontoffice.domain.stay.PendingStep;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus;
import io.mateu.ecdemo1.frontoffice.ui.common.FrontOffice;
import io.mateu.uidl.data.Notice;
import io.mateu.uidl.data.StatusItem;
import io.mateu.uidl.data.StatusList;
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
        : "Completar el check-in: falta lo siguiente. Hazlo y pulsa «Confirmar check-in».";
    return Notice.builder()
        .theme(status.overdue() ? "danger" : "warning")
        .text(text)
        .fullWidth(true)
        .content(List.of(steps(status)))
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
        + " No puede hacer el check-out hasta completarlo.";
    return Notice.builder()
        .id("checkin-incompleto")
        .theme(status.overdue() ? "danger" : "warning")
        .text(text)
        .fullWidth(true)
        .content(List.of(steps(status)))
        .actionLabel("Completar")
        .actionId("completarCheckin")
        .build();
  }

  static Component steps(IncompleteCheckIns.Status status) {
    return StatusList.builder()
        .compact(true)
        .frameless(true)
        .style("width: 100%;")
        .items(status.missing().stream().map(ForcedCheckInViews::item).toList())
        .build();
  }

  static StatusItem item(PendingStep step) {
    return StatusItem.builder()
        .id(step.document() ? "falta-doc-" + step.pax() : "falta-firma")
        .icon(step.document() ? "🪪" : "✍")
        .title(step.label())
        .status("Pendiente")
        .statusColor("warning")
        .build();
  }
}
