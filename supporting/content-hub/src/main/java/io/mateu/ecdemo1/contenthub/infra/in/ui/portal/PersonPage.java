package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui.esc;

/**
 * A person's card: who they are and where, how to reach them, the chain of managers above them, the
 * people who report to them and their team (same department, same hotel). Every person in it links to
 * their own card.
 */
@Service
@Scope("prototype")
public class PersonPage implements Identifiable {

    @Hidden
    String id;
    @Hidden
    String title;
    @Hidden
    String html;

    @Label("")
    @Colspan(2)
    Callable<Component> content = () -> Html.block(html == null ? "" : html);

    final PortalSupport portal;
    final People people;

    public PersonPage(PortalSupport portal, People people) {
        this.portal = portal;
        this.people = people;
    }

    public PersonPage load(String id, HttpRequest httpRequest) {
        portal.reader(httpRequest);
        this.id = id;
        var person = people.find(id).orElse(null);
        if (person == null) {
            title = "No encontrado";
            html = "<p>No hay nadie con ese usuario en el directorio.</p>";
            return this;
        }
        title = People.name(person);
        html = card(person);
        return this;
    }

    String card(Employee e) {
        var where = people.hotel(e).isEmpty() ? "Servicios centrales" : people.hotel(e);
        var html = new StringBuilder();
        html.append("<div style=\"display:flex;gap:1.25rem;align-items:center;margin-bottom:1rem\">")
                .append(People.avatar(e, 72)).append("<div><h1 style=\"margin:0\">").append(esc(People.name(e)))
                .append("</h1><div style=\"font-size:1.1em;opacity:.8\">").append(esc(people.jobTitle(e)))
                .append("</div><div style=\"opacity:.7\">").append(esc(join(people.department(e), where,
                        people.region(e)))).append("</div></div></div>");
        var contact = new StringBuilder();
        if (e.email() != null) {
            contact.append("<div>✉️ <a href=\"mailto:").append(esc(e.email())).append("\">").append(esc(e.email()))
                    .append("</a></div>");
        }
        if (e.phone() != null) {
            contact.append("<div>📞 <a href=\"tel:").append(esc(e.phone().replace(" ", ""))).append("\">")
                    .append(esc(e.phone())).append("</a></div>");
        }
        if (!contact.isEmpty()) {
            html.append(Ui.card("<h3 style=\"margin-top:0\">Contacto</h3>" + contact));
        }
        var chain = people.chainOf(e);
        if (!chain.isEmpty()) {
            html.append(Ui.card("<h3 style=\"margin-top:0\">Reporta a</h3>" + chain.stream().map(people::chip)
                    .collect(Collectors.joining("<div style=\"margin:.25rem 0 .25rem 1rem;opacity:.5\">↑</div>"))));
        }
        html.append(section("Su equipo directo", people.reportsOf(e.userId())));
        html.append(section("Mismo equipo", people.teamOf(e)));
        html.append("<p><a href=\"").append(Ui.PORTAL).append("/organigrama\">Ver el organigrama completo</a></p>");
        return html.toString();
    }

    String section(String title, List<Employee> list) {
        if (list.isEmpty()) {
            return "";
        }
        return Ui.card("<h3 style=\"margin-top:0\">" + esc(title) + " (" + list.size() + ")</h3>"
                + "<div style=\"display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:.75rem\">"
                + list.stream().map(people::chip).collect(Collectors.joining()) + "</div>");
    }

    static String join(String... parts) {
        return java.util.Arrays.stream(parts).filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(" · "));
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return title == null ? "Persona" : title;
    }
}
