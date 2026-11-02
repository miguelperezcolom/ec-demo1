package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.OrgChart;
import io.mateu.uidl.data.OrgNode;
import io.mateu.uidl.data.Tab;
import io.mateu.uidl.data.TabLayout;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.fluent.Form;
import io.mateu.uidl.interfaces.ComponentTreeSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The organisation with Mateu's org chart, in two tabs: who reports to whom, from the people nobody
 * else heads down; and the structure — company, region, hotel (or headquarters), department and the
 * people in it, the department's head first. A person opens their card.
 */
@Service
@Scope("prototype")
@Title("Organigrama")
public class OrgChartPage implements ComponentTreeSupplier {

    /** What a person's node runs: it opens their card. */
    static final String OPEN = "abrirPersona";
    static final String PERSON = "p:";

    final PortalSupport portal;
    final People people;

    public OrgChartPage(PortalSupport portal, People people) {
        this.portal = portal;
        this.people = people;
    }

    @Override
    public Component component(HttpRequest httpRequest) {
        portal.reader(httpRequest);
        var tabs = List.of(
                new Tab("Por jefes", new OrgChart("org-managers", managers(), null, null)),
                new Tab("Por estructura", new OrgChart("org-structure", structure(), null, null)));
        return Form.builder().title("Organigrama")
                .subtitle(people.all().size() + " personas")
                .content(List.of(TabLayout.builder().id("org-tabs").tabs(tabs).build()))
                .build();
    }

    /** The heads (nobody in the directory above them) and everyone below; one root, or the company over several. */
    OrgNode managers() {
        var all = people.all();
        var heads = all.stream().filter(e -> e.managerId() == null || people.find(e.managerId()).isEmpty()).toList();
        var seen = new HashSet<String>();
        var roots = heads.stream().map(h -> reportsTree(h, seen)).toList();
        if (roots.size() == 1) {
            return roots.getFirst();
        }
        var company = all.isEmpty() ? "La empresa" : people.company(all.getFirst());
        return OrgNode.builder().id("company").title(company).subtitle("Dirección").children(roots).build();
    }

    OrgNode reportsTree(Employee e, Set<String> seen) {
        seen.add(e.userId());
        var children = people.reportsOf(e.userId()).stream().filter(r -> !seen.contains(r.userId()))
                .map(r -> reportsTree(r, seen)).toList();
        return node(e, children);
    }

    OrgNode structure() {
        var companies = new ArrayList<OrgNode>();
        people.byStructure().forEach((company, regions) -> {
            var regionNodes = new ArrayList<OrgNode>();
            regions.forEach((region, hotels) -> {
                var hotelNodes = new ArrayList<OrgNode>();
                hotels.forEach((hotel, departments) -> {
                    var departmentNodes = new ArrayList<OrgNode>();
                    departments.forEach((department, staff) -> departmentNodes.add(group(
                            "d:" + company + "/" + region + "/" + hotel + "/" + department, department, staff.size(),
                            headFirst(staff).stream().map(e -> node(e, List.of())).toList())));
                    hotelNodes.add(group("h:" + company + "/" + region + "/" + hotel, hotel,
                            departments.values().stream().mapToInt(List::size).sum(), departmentNodes));
                });
                regionNodes.add(group("r:" + company + "/" + region, region.isEmpty() ? "Sin región" : region,
                        hotels.values().stream().flatMap(d -> d.values().stream()).mapToInt(List::size).sum(), hotelNodes));
            });
            companies.add(group("c:" + company, company, -1, regionNodes));
        });
        return companies.size() == 1 ? companies.getFirst()
                : OrgNode.builder().id("all").title("Organización").children(companies).build();
    }

    OrgNode node(Employee e, List<OrgNode> children) {
        return node(e, people, children);
    }

    /** A person as an org chart node: their name and job; clicking it opens their card. */
    static OrgNode node(Employee e, People people, List<OrgNode> children) {
        return OrgNode.builder().id(PERSON + e.userId()).title(People.name(e)).subtitle(people.jobTitle(e))
                .actionId(OPEN).children(children).build();
    }

    /** The card of the person whose node was clicked, or null when the click was on no person. */
    static Object open(HttpRequest httpRequest) {
        var parameters = httpRequest.runActionRq().parameters();
        if (parameters != null && parameters.get("_clickedNode") instanceof Map<?, ?> node
                && node.get("id") instanceof String id && id.startsWith(PERSON)) {
            return URI.create(People.link(id.substring(PERSON.length())));
        }
        return null;
    }

    static OrgNode group(String id, String title, int size, List<OrgNode> children) {
        return OrgNode.builder().id(id).title(title).subtitle(size < 0 ? null : size + (size == 1 ? " persona" : " personas"))
                .children(children).build();
    }

    /** Whoever the others in the department report to goes first. */
    static List<Employee> headFirst(List<Employee> staff) {
        return staff.stream().sorted(java.util.Comparator.comparing(
                (Employee e) -> staff.stream().noneMatch(o -> e.userId().equals(o.managerId())))).toList();
    }

    /** A person's node was clicked: their card. */
    @Action
    public Object abrirPersona(HttpRequest httpRequest) {
        return open(httpRequest);
    }
}
