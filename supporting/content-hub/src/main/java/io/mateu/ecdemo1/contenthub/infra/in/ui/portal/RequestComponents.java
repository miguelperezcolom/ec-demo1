package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.domain.request.EmployeeRequest;
import io.mateu.ecdemo1.contenthub.domain.request.RequestStatus;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.data.Badge;
import io.mateu.uidl.data.Chip;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;
import io.mateu.uidl.data.Text;
import io.mateu.uidl.data.Timeline;
import io.mateu.uidl.data.TimelineItem;
import io.mateu.uidl.fluent.Component;

import java.util.ArrayList;
import java.util.List;

import static io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PageRenderer.section;

/** A request drawn with Mateu's components — what was asked, and its history as a timeline. */
final class RequestComponents {

    private RequestComponents() {
    }

    /** The colour of a status, as Mateu's badges name them. */
    static String color(RequestStatus s) {
        return switch (s) {
            case APROBADA -> "success";
            case RECHAZADA -> "error";
            default -> "warning";
        };
    }

    /** The status as a listing's badge. */
    static Status state(EmployeeRequest r) {
        return new Status(switch (r.status()) {
            case APROBADA -> StatusType.SUCCESS;
            case RECHAZADA -> StatusType.DANGER;
            default -> StatusType.WARNING;
        }, r.status().label());
    }

    static Chip status(EmployeeRequest r) {
        return new Chip(r.status().label(), color(r.status()));
    }

    static String dates(EmployeeRequest r) {
        return r.from() == null ? "" : r.from() + (r.to() == null || r.to().equals(r.from()) ? "" : " → " + r.to());
    }

    /** "Vacaciones · 2026-11-10 → 2026-11-14", the page's title. */
    static String title(EmployeeRequest r) {
        return r.type().label() + (dates(r).isEmpty() ? "" : " · " + dates(r));
    }

    /** "Pedida por Ana Pérez el 02/11/2026", the page's subtitle. */
    static String subtitle(EmployeeRequest r) {
        return "Pedida por " + r.employeeName() + " el " + Ui.day(r.createdAt());
    }

    /** The request's sections: its status and detail, and its history. */
    static List<Component> sections(EmployeeRequest r) {
        var out = new ArrayList<Component>();
        var detail = new ArrayList<Component>();
        detail.add(Badge.builder().text(r.status().label()).color(color(r.status())).pill(true).build());
        if (r.description() != null && !r.description().isBlank()) {
            detail.add(Text.builder().text(r.description()).build());
        }
        out.add(section("Detalle", PortalComponents.column(detail)));
        out.add(section("Historial", history(r)));
        return out;
    }

    static Timeline history(EmployeeRequest r) {
        var items = new ArrayList<TimelineItem>();
        var n = 0;
        for (var s : r.history()) {
            var description = new ArrayList<String>();
            if (s.by() != null) {
                description.add(s.by());
            }
            if (s.comment() != null) {
                description.add("«" + s.comment() + "»");
            }
            items.add(TimelineItem.builder().id("step-" + n++).title(s.status().label())
                    .description(String.join(" · ", description)).timestamp(Ui.when(s.at()))
                    .color(color(s.status())).build());
        }
        return Timeline.builder().id("request-history").items(items).build();
    }
}
