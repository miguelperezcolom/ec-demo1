package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.ecdemo1.booking.infra.out.mdm.CustomerLinks;
import io.mateu.uidl.data.Element;
import io.mateu.uidl.data.Text;
import io.mateu.uidl.fluent.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A booking in the chain's other systems, as links: its reservation and guest profiles in Opera (as
 * references — Opera Cloud has no stable deep link), its stay in the front office, and its people
 * in Clientes and in Salesforce. Markup in an {@link Element}: the one way a link is drawn alike by
 * the Vaadin and the Redwood renderers inside a form. Data is escaped. One Element per page: Mateu
 * gives every Element the same id.
 */
final class OtherSystems {

    static final String CELL = "text-align: left; padding: .3rem .5rem; border-bottom: 1px solid rgba(128, 128, 128, .25); vertical-align: top;";

    record Row(String what, String label, String href) {
    }

    private OtherSystems() {
    }

    static Component of(String pmsReservationId, CustomerLinks.ReservationLinks links) {
        var rows = rows(pmsReservationId, links);
        if (rows.isEmpty()) {
            return Text.builder().text("Not in any other system yet.").build();
        }
        var html = new StringBuilder("<table style=\"width: 100%; border-collapse: collapse; font-size: .875rem;\"><tbody>");
        for (var row : rows) {
            html.append("<tr><th style=\"").append(CELL).append(" font-weight: 600; opacity: .75; white-space: nowrap;\">")
                    .append(escape(row.what())).append("</th><td style=\"").append(CELL).append("\">");
            if (row.href() == null || row.href().isBlank()) {
                html.append(escape(row.label()));
            } else {
                var external = row.href().startsWith("http");
                html.append("<a href=\"").append(escape(row.href())).append("\"")
                        .append(external ? " target=\"_blank\" rel=\"noopener\"" : "").append(">")
                        .append(escape(row.label())).append("</a>");
            }
            html.append("</td></tr>");
        }
        return Element.html("div", Map.of("style", "width: 100%;"), html.append("</tbody></table>").toString());
    }

    static List<Row> rows(String pmsReservationId, CustomerLinks.ReservationLinks links) {
        var rows = new ArrayList<Row>();
        if (pmsReservationId != null && !pmsReservationId.isBlank()) {
            rows.add(new Row("Opera · reservation", pmsReservationId, null));
        }
        if (links == null) {
            return rows;
        }
        if (links.operaProfiles() != null) {
            links.operaProfiles().forEach(p -> rows.add(new Row("Opera · guest profile", p.profileId(), null)));
        }
        if (links.frontOffice() != null) {
            rows.add(new Row("Front office · stay", stayStatus(links.frontOffice().status()), links.frontOffice().url()));
        }
        if (links.passengers() != null) {
            for (var p : links.passengers()) {
                var who = "HOLDER".equals(p.role()) ? "Holder" : "Guest " + (p.passenger() + 1);
                rows.add(new Row(who + " · customer", p.name() + " · " + p.customerId(), p.customerRoute()));
                if (p.salesforceContactId() != null) {
                    rows.add(new Row(who + " · Salesforce", "Contact " + p.salesforceContactId(), p.salesforceContactUrl()));
                }
            }
        }
        return rows;
    }

    static String stayStatus(String status) {
        if (status == null) {
            return "Stay";
        }
        return switch (status) {
            case "ARRIVING" -> "Arriving";
            case "IN_HOUSE" -> "In house";
            case "DEPARTED" -> "Departed";
            case "CANCELLED" -> "Cancelled";
            case "NO_SHOW" -> "No show";
            default -> status;
        };
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
                // both renderers interpolate ${…} in an Element's content
                case '$' -> out.append("&#36;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
