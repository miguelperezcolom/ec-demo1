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
    var links = CrossLinks.of(stayId);
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

  static String escape(String text) {
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
