package io.mateu.ecdemo1.frontoffice.ui.common;

import io.mateu.ecdemo1.frontoffice.application.GuestNotices;
import io.mateu.ecdemo1.frontoffice.domain.guest.CustomerNotice;
import io.mateu.uidl.data.StatusItem;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** How the desk sees the reception notices and the check-out warnings: one row, or one line, each. */
public final class NoticeItems {

  static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private NoticeItems() {
  }

  /** One row per notice: the pax, the text, its type as the status (a blocking one in red). */
  public static List<StatusItem> items(List<GuestNotices.PaxNotice> notices) {
    var items = new ArrayList<StatusItem>();
    for (var p : notices) {
      var n = p.notice();
      items.add(StatusItem.builder()
          .id("aviso-" + n.noticeId())
          .icon(n.blocking() ? "⛔" : n.type() == CustomerNotice.Type.IMPORTANT ? "⚠" : "ℹ")
          .title(n.text())
          .description(p.guestName() + (p.pax() == 1 ? " (titular)" : " (pax " + p.pax() + ")") + validity(n))
          .status(n.typeLabel())
          .statusColor(color(n))
          .build());
    }
    return items;
  }

  /** The notices of one pax, as lines under their row in the guests' rail. */
  public static List<String> lines(List<GuestNotices.PaxNotice> notices, int pax) {
    return notices.stream().filter(p -> p.pax() == pax)
        .map(p -> (p.notice().blocking() ? "⛔ " : p.notice().type() == CustomerNotice.Type.IMPORTANT ? "⚠ " : "ℹ ")
            + "Aviso " + p.notice().typeLabel().toLowerCase() + ": " + p.notice().text())
        .toList();
  }

  /** One row per kárdex warning of the check-out: rejected in red, pending in amber. */
  public static List<StatusItem> kardexItems(List<GuestNotices.KardexWarning> warnings) {
    return warnings.stream().map(k -> StatusItem.builder()
        .id("kardex-" + k.pax())
        .icon(k.rejected() ? "✖" : "⏳")
        .title("Kárdex de " + k.guestName())
        .description(k.rejected() ? "Salesforce rechazó el cambio de recepción"
            : "Salesforce no ha decidido el cambio: la factura saldrá con el dato anterior")
        .status(k.rejected() ? "Rechazado" : "Pendiente")
        .statusColor(k.rejected() ? "error" : "warning")
        .lines(k.lines())
        .build()).toList();
  }

  static String color(CustomerNotice n) {
    return switch (n.type()) {
      case BLOCKING -> "error";
      case IMPORTANT -> "warning";
      case INFORMATIVE -> "info";
    };
  }

  static String validity(CustomerNotice n) {
    if (n.from() == null && n.to() == null) {
      return "";
    }
    return " · " + (n.from() == null ? "…" : DAY.format(n.from())) + " → " + (n.to() == null ? "…" : DAY.format(n.to()));
  }
}
