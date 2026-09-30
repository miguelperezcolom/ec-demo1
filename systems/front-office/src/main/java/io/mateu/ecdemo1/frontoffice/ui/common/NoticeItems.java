package io.mateu.ecdemo1.frontoffice.ui.common;

import io.mateu.ecdemo1.frontoffice.application.GuestNotices;
import io.mateu.ecdemo1.frontoffice.domain.notice.Notice;
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
          .icon(n.blocking() ? "⛔" : n.type() == Notice.Type.IMPORTANT ? "⚠" : "ℹ")
          .title(n.text())
          .description(about(p) + validity(n))
          .status(n.typeLabel())
          .statusColor(color(n))
          .build());
    }
    return items;
  }

  /**
   * What a notice is about, as the desk reads it: «Cliente · Ana García (titular)», «Cliente · Luis (pax
   * 2)», «Reserva 12E45», «Agencia Nordic Travel Group AB».
   */
  public static String about(GuestNotices.PaxNotice p) {
    if (!p.onPax()) {
      return p.guestName();
    }
    return "Cliente · " + p.guestName() + (p.pax() == 1 ? " (titular)" : " (pax " + p.pax() + ")");
  }

  /** The notices of the reservation and its agency (none of a pax), as rows under a heading. */
  public static List<StatusItem> stayItems(List<GuestNotices.PaxNotice> notices) {
    return items(notices.stream().filter(p -> !p.onPax()).toList());
  }

  /**
   * A heading notice and the rows under it — amber, red if one is blocking — or nothing at all when
   * there are none: an empty block would still take its place on the panel.
   */
  public static io.mateu.uidl.fluent.Component block(String heading, List<GuestNotices.PaxNotice> notices) {
    if (notices.isEmpty()) {
      return new io.mateu.uidl.data.VerticalLayout();
    }
    var blocking = notices.stream().anyMatch(p -> p.notice().blocking());
    return io.mateu.uidl.data.VerticalLayout.builder()
        .style("width: 100%; gap: .5rem;")
        .content(List.of(
            io.mateu.uidl.data.Notice.builder().theme(blocking ? "danger" : "warning").text(heading).slim(true)
                .fullWidth(true).build(),
            io.mateu.uidl.data.StatusList.builder().items(items(notices)).compact(true).style("width: 100%;").build()))
        .build();
  }

  /** The notices of one pax, as lines under their row in the guests' rail. */
  public static List<String> lines(List<GuestNotices.PaxNotice> notices, int pax) {
    return notices.stream().filter(p -> p.pax() == pax)
        .map(p -> (p.notice().blocking() ? "⛔ " : p.notice().type() == Notice.Type.IMPORTANT ? "⚠ " : "ℹ ")
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

  static String color(Notice n) {
    return switch (n.type()) {
      case BLOCKING -> "error";
      case IMPORTANT -> "warning";
      case INFORMATIVE -> "info";
    };
  }

  static String validity(Notice n) {
    if (n.from() == null && n.to() == null) {
      return "";
    }
    return " · " + (n.from() == null ? "…" : DAY.format(n.from())) + " → " + (n.to() == null ? "…" : DAY.format(n.to()));
  }
}
