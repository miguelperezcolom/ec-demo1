package io.mateu.ecdemo1.frontoffice.ui.common;

import io.mateu.ecdemo1.frontoffice.infra.mdm.CrossLinks;
import io.mateu.uidl.data.Element;
import io.mateu.uidl.data.Text;
import io.mateu.uidl.fluent.Component;
import java.util.Map;

/**
 * A stay in the chain's other systems, as links: the CRS booking, the customer in Clientes, the
 * contact in Salesforce — each opened in a new tab — and the Opera profile as a reference to copy.
 * Markup in an {@link Element}, the one way a link is drawn by the Redwood renderer inside a form;
 * data is escaped. One Element per page: Mateu gives every Element the same id.
 */
public final class OtherSystems {

  static final String CELL =
      "text-align: left; padding: .3rem .5rem; border-bottom: 1px solid rgba(128, 128, 128, .25); vertical-align: top;";

  private OtherSystems() {}

  public static Component of(String stayId) {
    var links = links(stayId);
    if (links.isEmpty()) {
      return Text.builder().text("Esta estancia no viene de la central de reservas.").noMargins(true).build();
    }
    var html = new StringBuilder("<table style=\"width: 100%; border-collapse: collapse; font-size: .875rem;\"><tbody>");
    for (var link : links) {
      html.append("<tr><th style=\"").append(CELL).append(" font-weight: 600; opacity: .75; white-space: nowrap;\">")
          .append(escape(link.what())).append("</th><td style=\"").append(CELL).append("\">");
      if (link.href() == null || link.href().isBlank()) {
        html.append(escape(link.label()));
      } else {
        html.append("<a href=\"").append(escape(link.href())).append("\" target=\"_blank\" rel=\"noopener\">")
            .append(escape(link.label())).append("</a>");
      }
      html.append("</td></tr>");
    }
    html.append("</tbody></table>");
    return Element.html("div", Map.of("style", "width: 100%;"), html.toString());
  }

  /**
   * The same, as a list the arrival's foldout draws in a panel: the Redwood renderer mounts an
   * {@link Element} only in the page's content, not inside a foldout panel, so there it would stay
   * empty. What and where, as text — the links open from the stay and the departure, where the
   * table is a page section.
   */
  public static Component asList(String stayId) {
    var links = links(stayId);
    if (links.isEmpty()) {
      return Text.builder().text("Esta estancia no viene de la central de reservas.").noMargins(true).build();
    }
    return io.mateu.uidl.data.StatusList.builder().compact(true).frameless(true).style("width: 100%;")
        .items(links.stream().map(link -> io.mateu.uidl.data.StatusItem.builder()
            .id(link.what())
            .title(link.what())
            .description(link.label())
            .build()).toList())
        .build();
  }

  static java.util.List<CrossLinks.Link> links(String stayId) {
    var links = new java.util.ArrayList<CrossLinks.Link>();
    // The PMS is the master of the stay: which Opera reservation it is, and where the desk's check-in,
    // check-out or no-show stands there.
    var pms = FrontOffice.pmsReservation(stayId);
    var state = FrontOffice.pmsState(stayId);
    if (pms.isPresent() || state.isPresent()) {
      links.add(new CrossLinks.Link("Opera", pms.map(id -> "Reserva " + id).orElse("Reserva sin enlazar")
          + state.map(s -> " — " + s).orElse(""), null));
    }
    // A closed stay's invoice: the PMS's, or the front office's proforma — in a tab of its own.
    var stay = FrontOffice.stayView(stayId).stay();
    if (stay.status() == io.mateu.ecdemo1.frontoffice.domain.stay.StayStatus.DEPARTED) {
      links.add(new CrossLinks.Link("Factura", FrontOffice.invoice(stayId).label(), FrontOffice.invoiceLink(stayId)));
    }
    links.addAll(CrossLinks.of(stayId));
    return links;
  }

  public static String escape(String text) {
    if (text == null) {
      return "";
    }
    var out = new StringBuilder(text.length());
    for (var c : text.toCharArray()) {
      switch (c) {
        case '<' -> out.append("&lt;");
        case '>' -> out.append("&gt;");
        case '&' -> out.append("&amp;");
        case '"' -> out.append("&quot;");
        case '\'' -> out.append("&#39;");
        // the renderer interpolates ${…} in an Element's content
        case '$' -> out.append("&#36;");
        default -> out.append(c);
      }
    }
    return out.toString();
  }
}
