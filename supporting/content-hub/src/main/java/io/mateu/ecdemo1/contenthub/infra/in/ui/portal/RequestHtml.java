package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.domain.request.EmployeeRequest;
import io.mateu.ecdemo1.contenthub.domain.request.RequestStatus;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;

import java.util.stream.Collectors;

import static io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui.esc;

/** A request drawn as markup — its summary and its history — the same on both renderers. */
final class RequestHtml {

    private RequestHtml() {
    }

    static String color(RequestStatus s) {
        return switch (s) {
            case APROBADA -> "#067647";
            case RECHAZADA -> "#b42318";
            default -> "#9e4c0a";
        };
    }

    static String dates(EmployeeRequest r) {
        return r.from() == null ? "" : r.from() + (r.to() == null || r.to().equals(r.from()) ? "" : " → " + r.to());
    }

    static String summary(EmployeeRequest r) {
        return "<h2 style=\"margin:0 0 .5rem\">" + esc(r.type().label()) + (dates(r).isEmpty() ? "" : " · " + esc(dates(r)))
                + "</h2><p>" + Ui.chip(r.status().label(), color(r.status())) + "<span style=\"opacity:.7\"> pedida por "
                + esc(r.employeeName()) + " el " + Ui.day(r.createdAt()) + "</span></p>"
                + (r.description() == null || r.description().isBlank() ? "" : Ui.card("<b>Detalle</b><p style=\"margin-bottom:0\">"
                + esc(r.description()) + "</p>"));
    }

    static String history(EmployeeRequest r) {
        return Ui.card("<h3 style=\"margin-top:0\">Historial</h3><ul style=\"list-style:none;padding:0;margin:0\">"
                + r.history().stream().map(s -> "<li style=\"padding:.35rem 0;border-bottom:1px solid rgba(128,128,128,.15)\">"
                + Ui.when(s.at()) + " · " + Ui.chip(s.status().label(), color(s.status()))
                + (s.by() == null ? "" : " <span style=\"opacity:.7\">" + esc(s.by()) + "</span>")
                + (s.comment() == null ? "" : "<div style=\"opacity:.8;margin-top:.2rem\">«" + esc(s.comment()) + "»</div>")
                + "</li>").collect(Collectors.joining()) + "</ul>");
    }
}
