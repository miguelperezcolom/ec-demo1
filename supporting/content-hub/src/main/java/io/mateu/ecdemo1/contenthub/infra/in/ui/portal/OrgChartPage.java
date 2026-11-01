package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.ComponentTreeSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui.esc;

/**
 * The organisation as a tree — company, region, hotel (or headquarters), department, and the people
 * in it, the department's head first — and, above it, who leads it: the person nobody else heads,
 * with their direct reports. One block of markup, drawn the same by both renderers.
 */
@Service
@Scope("prototype")
@Title("Organigrama")
public class OrgChartPage implements ComponentTreeSupplier {

    static final String LEVEL = "list-style:none;margin:.25rem 0 .5rem;padding-left:1.25rem;"
            + "border-left:2px solid rgba(128,128,128,.25)";

    final PortalSupport portal;
    final People people;

    public OrgChartPage(PortalSupport portal, People people) {
        this.portal = portal;
        this.people = people;
    }

    @Override
    public Component component(HttpRequest httpRequest) {
        portal.reader(httpRequest);
        var html = new StringBuilder("<h1 style=\"margin:.25rem 0 1rem\">Organigrama</h1>");
        var all = people.all();
        var heads = all.stream().filter(e -> e.managerId() == null || people.find(e.managerId()).isEmpty()).toList();
        if (!heads.isEmpty()) {
            html.append("<h2>Dirección</h2>");
            for (var head : heads) {
                html.append("<div style=\"margin-bottom:.5rem\">").append(people.chip(head)).append("</div>")
                        .append(people(people.reportsOf(head.userId())));
            }
        }
        html.append("<h2>Por estructura</h2>");
        people.byStructure().forEach((company, regions) -> {
            html.append("<h3 style=\"margin:.75rem 0 .25rem\">🏢 ").append(esc(company)).append("</h3><ul style=\"")
                    .append(LEVEL).append("\">");
            regions.forEach((region, hotels) -> {
                html.append("<li><b>📍 ").append(esc(region.isEmpty() ? "Sin región" : region)).append("</b><ul style=\"")
                        .append(LEVEL).append("\">");
                hotels.forEach((hotel, departments) -> {
                    var size = departments.values().stream().mapToInt(List::size).sum();
                    html.append("<li><b>🏨 ").append(esc(hotel)).append("</b> <span style=\"opacity:.6\">(")
                            .append(size).append(")</span><ul style=\"").append(LEVEL).append("\">");
                    departments.forEach((department, staff) -> html.append("<li><b>").append(esc(department))
                            .append("</b>").append(people(headFirst(staff))).append("</li>"));
                    html.append("</ul></li>");
                });
                html.append("</ul></li>");
            });
            html.append("</ul>");
        });
        return Html.block(html.toString());
    }

    /** Whoever the others in the department report to goes first. */
    static List<Employee> headFirst(List<Employee> staff) {
        return staff.stream().sorted(java.util.Comparator.comparing(
                (Employee e) -> staff.stream().noneMatch(o -> e.userId().equals(o.managerId())))).toList();
    }

    String people(List<Employee> list) {
        if (list.isEmpty()) {
            return "";
        }
        return "<div style=\"display:flex;flex-wrap:wrap;gap:.5rem 1.25rem;margin:.35rem 0 .5rem 1.25rem\">"
                + list.stream().map(people::chip).collect(Collectors.joining()) + "</div>";
    }
}
