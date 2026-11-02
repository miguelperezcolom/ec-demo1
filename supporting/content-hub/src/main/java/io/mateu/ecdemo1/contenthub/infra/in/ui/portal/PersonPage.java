package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.data.Anchor;
import io.mateu.uidl.data.OrgChart;
import io.mateu.uidl.data.OrgChartDisplay;
import io.mateu.uidl.data.EmptyState;
import io.mateu.uidl.data.FieldDataType;
import io.mateu.uidl.data.FormField;
import io.mateu.uidl.data.ItemOverviewPage;
import io.mateu.uidl.data.Tab;
import io.mateu.uidl.data.TabLayout;
import io.mateu.uidl.data.TemplateImage;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.fluent.Form;
import io.mateu.uidl.interfaces.ComponentTreeSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * A person's card on Mateu's item overview page template (Redwood draws Spectra's): the overview
 * panel — their initials, name and job, where they work and how to reach them — beside tabs with the
 * chain of managers above them, the people who report to them and their team (same department, same
 * hotel). Every person in it opens their own card.
 */
@Service
@Scope("prototype")
public class PersonPage implements ComponentTreeSupplier, Identifiable {

    String id;
    // What the overview panel's property rows read from the state.
    String department;
    String workplace;
    String region;
    String email;
    String phone;

    final PortalSupport portal;
    final People people;

    public PersonPage(PortalSupport portal, People people) {
        this.portal = portal;
        this.people = people;
    }

    public PersonPage load(String id, HttpRequest httpRequest) {
        this.id = id;
        return this;
    }

    @Override
    public Component component(HttpRequest httpRequest) {
        return PortalComponents.namingTheTab(httpRequest, draw(httpRequest));
    }

    Component draw(HttpRequest httpRequest) {
        if (id == null) {
            id = httpRequest.lastPathItem();
        }
        portal.reader(httpRequest);
        var person = people.find(id).orElse(null);
        if (person == null) {
            return Form.builder().title("No encontrado")
                    .content(List.of(EmptyState.builder().title("No hay nadie con ese usuario en el directorio").build()))
                    .build();
        }
        department = people.department(person);
        workplace = people.hotel(person).isEmpty() ? "Servicios centrales" : people.hotel(person);
        region = people.region(person);
        email = person.email();
        phone = person.phone();
        var body = new ArrayList<Component>();
        body.add(row("department", "Departamento"));
        body.add(row("workplace", "Dónde"));
        if (!region.isEmpty()) {
            body.add(row("region", "Región"));
        }
        if (email != null) {
            body.add(new Anchor(email, "mailto:" + email));
        }
        if (phone != null) {
            body.add(new Anchor(phone, "tel:" + phone.replace(" ", "")));
        }
        return Form.builder()
                .title(People.name(person))
                .subtitle(people.jobTitle(person))
                .template(ItemOverviewPage.builder()
                        .avatar(new TemplateImage(null, People.initials(People.name(person))))
                        .body(body)
                        .footerItem(new Anchor("Ver el organigrama", Ui.PORTAL + "/organigrama"))
                        .build())
                .content(List.of(tabs(person)))
                .build();
    }

    /**
     * The person among the others: the company's org chart in focus on them — their managers above,
     * their reports below, a click away from anyone else's — and their team (same department, same
     * workplace).
     */
    TabLayout tabs(Employee person) {
        var team = people.teamOf(person);
        var tabs = new ArrayList<Tab>();
        tabs.add(new Tab("Organigrama", OrgChart.builder().id("person-org").root(OrgChartPage.managers(people))
                .display(OrgChartDisplay.focus).focus(OrgChartPage.PERSON + person.userId()).build()));
        if (!team.isEmpty()) {
            tabs.add(new Tab("Mismo equipo (" + team.size() + ")", new OrgChart("person-team",
                    OrgChartPage.group("team", department + " · " + workplace, team.size() + 1,
                            java.util.stream.Stream.concat(java.util.stream.Stream.of(person), team.stream())
                                    .map(e -> OrgChartPage.node(e, people, List.of())).toList()), null, null)));
        }
        return TabLayout.builder().id("person-tabs").tabs(tabs).build();
    }

    /** A person's node was clicked: their card. */
    @Action
    public Object abrirPersona(HttpRequest httpRequest) {
        return OrgChartPage.open(httpRequest);
    }

    static FormField row(String id, String label) {
        return FormField.builder().id(id).label(label).dataType(FieldDataType.string).readOnly(true)
                .propertyRow(true).build();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return "Persona";
    }
}
