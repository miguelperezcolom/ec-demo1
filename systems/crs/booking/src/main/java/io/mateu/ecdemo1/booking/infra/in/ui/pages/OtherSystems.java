package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.ecdemo1.booking.infra.out.mdm.CustomerLinks;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.data.Text;
import io.mateu.uidl.fluent.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A booking in the chain's other systems, as links: its reservation and guest profiles in Opera (as
 * references — Opera Cloud has no stable deep link), its stay in the front office, and its people
 * in Clientes and in Salesforce. Drawn as the shared {@link Html#linksTable}: markup the Vaadin and
 * the Redwood renderers draw alike inside a form, with the data escaped. One per page: Mateu gives
 * every Element the same id.
 */
final class OtherSystems {

    private OtherSystems() {
    }

    static Component of(String pmsReservationId, CustomerLinks.ReservationLinks links) {
        var rows = rows(pmsReservationId, links);
        if (rows.isEmpty()) {
            return Text.builder().text("Not in any other system yet.").build();
        }
        return Html.block(Html.linksTable(rows));
    }

    static List<Html.Link> rows(String pmsReservationId, CustomerLinks.ReservationLinks links) {
        var rows = new ArrayList<Html.Link>();
        if (pmsReservationId != null && !pmsReservationId.isBlank()) {
            rows.add(new Html.Link("Opera · reservation", pmsReservationId, null));
        }
        if (links == null) {
            return rows;
        }
        if (links.operaProfiles() != null) {
            links.operaProfiles().forEach(p -> rows.add(new Html.Link("Opera · guest profile", p.profileId(), null)));
        }
        if (links.frontOffice() != null) {
            rows.add(new Html.Link("Front office · stay", stayStatus(links.frontOffice().status()), links.frontOffice().url()));
        }
        if (links.passengers() != null) {
            for (var p : links.passengers()) {
                var who = "HOLDER".equals(p.role()) ? "Holder" : "Guest " + (p.passenger() + 1);
                rows.add(new Html.Link(who + " · customer", p.name() + " · " + p.customerId(), p.customerRoute()));
                if (p.salesforceContactId() != null) {
                    rows.add(new Html.Link(who + " · Salesforce", "Contact " + p.salesforceContactId(), p.salesforceContactUrl()));
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
}
