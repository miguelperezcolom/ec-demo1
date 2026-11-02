package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.search.IntranetSearch;
import io.mateu.ecdemo1.contenthub.application.usecases.UsageTracker;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.data.Anchor;
import io.mateu.uidl.data.Button;
import io.mateu.uidl.data.ButtonStyle;
import io.mateu.uidl.data.FieldDataType;
import io.mateu.uidl.data.FormField;
import io.mateu.uidl.data.HorizontalLayout;
import io.mateu.uidl.data.MultimediaContentPage;
import io.mateu.uidl.data.Text;
import io.mateu.uidl.data.VerticalAlignment;
import io.mateu.uidl.data.VerticalLayout;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.fluent.Form;
import io.mateu.uidl.interfaces.ComponentTreeSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PageRenderer.section;
import static io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PortalComponents.column;
import static io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PortalComponents.literal;
import static io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PortalComponents.muted;
import static io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PortalComponents.small;

/**
 * The portal's search, on the multimedia content template and not on HeroSearchPage: on Redwood that one
 * brings its own search box, tied to a listing, which this page's content does not fit. The box; the AI's answer, for a query that reads as a
 * question (for any other, a button that asks for it); what the reader may see, by words and by meaning, each
 * result with the passage that matched; and the people the query names, as a group of their own.
 *
 * <p>A page and not a listing because the answer and the people go with the same query as the results.
 * Merged rankings do not page: the best {@link #LIMIT}.
 */
@Service
@Scope("prototype")
public class SearchPage implements ComponentTreeSupplier {

    static final int LIMIT = 20;
    static final int PEOPLE = 6;

    /** What is being searched: the box's value, kept in the page's state. */
    String q;
    /** The query the reader asked the AI about with the button: a query that does not read as a question. */
    String askedFor;

    /**
     * What was found for a query, once per request. Mateu calls {@link #component} more than once in a round
     * trip (to look for the action's button, then to draw), and each time here would ask the agent again, on the
     * reader's budget. Final, so it is no part of the page's state.
     */
    final Map<String, List<Component>> found = new HashMap<>();

    final PortalSupport portal;
    final IntranetSearch search;
    final People people;
    final UsageTracker usage;

    public SearchPage(PortalSupport portal, IntranetSearch search, People people, UsageTracker usage) {
        this.portal = portal;
        this.search = search;
        this.people = people;
        this.usage = usage;
    }

    @Override
    public Component component(HttpRequest httpRequest) {
        return PortalComponents.namingTheTab(httpRequest, draw(httpRequest));
    }

    Component draw(HttpRequest httpRequest) {
        var content = new ArrayList<Component>();
        var reader = portal.reader(httpRequest);
        content.addAll(PortalComponents.viewingAs(reader.viewingAs()));
        content.add(box());
        var query = q == null ? "" : q.trim();
        if (!query.isEmpty()) {
            var ask = query.equals(askedFor);
            content.addAll(found.computeIfAbsent(query + "|" + ask, k -> find(reader, query, ask,
                    httpRequest.getHeaderValue("Authorization"))));
        }
        return Form.builder().title("Buscar en la intranet")
                .subtitle("Por palabras y por significado, entre lo que puedes ver; y a quien se llame así")
                .template(new MultimediaContentPage(PortalComponents.TONE.name()))
                .content(content)
                .build();
    }

    List<Component> find(Reader who, String query, boolean ask, String authorization) {
        var reader = who.attributes();
        var out = new ArrayList<Component>();
        out.add(answerPanel(reader, query, ask, authorization));
        var persons = people(query);
        var results = search.search(reader, query, LIMIT);
        // one search: asking the AI about what was just searched is not another
        if (!ask) {
            usage.searched(who, query, results.items().size());
        }
        out.add(results(reader.language(), results, query, !persons.isEmpty()));
        if (!persons.isEmpty()) {
            out.add(section("Personas (" + persons.size() + ")",
                    PortalComponents.withLink(PortalComponents.people(persons, people, ""), "Todo el directorio",
                            Ui.PORTAL + "/personas")));
        }
        return out;
    }

    Component box() {
        return HorizontalLayout.builder().spacing(true).fullWidth(true)
                .verticalAlignment(VerticalAlignment.END).flexGrows(List.of(1, 0))
                .content(List.of(
                        FormField.builder().id("q").label("¿Qué buscas?").dataType(FieldDataType.string)
                                .placeholder("vacaciones, ¿cómo pido un cambio de turno?, Ana…").autofocus(true).build(),
                        Button.builder().label("Buscar").actionId("buscar").buttonStyle(ButtonStyle.primary)
                                .shortcut("Enter").build()))
                .build();
    }

    /** People by name or job: not for a whole sentence, nor for so little that it matches anybody. */
    List<Employee> people(String query) {
        return query.length() < 3 || IntranetSearch.looksLikeQuestion(query) ? List.of()
                : people.search(query).stream().limit(PEOPLE).toList();
    }

    /**
     * «Respuesta de la IA». The answer goes in the same request when the query reads as a question (or the
     * reader asked): results and answer arrive together. Otherwise no agent call nobody asked for. It goes
     * with the reader's own token, which is who the agent acts for and whose budget is charged.
     */
    Component answerPanel(EmployeeAttributes reader, String query, boolean ask, String authorization) {
        if (search.answersAvailable() && !ask && !IntranetSearch.looksLikeQuestion(query)) {
            return section("Respuesta de la IA", Button.builder().label("Preguntar a la IA").actionId("preguntar").build());
        }
        return section("Respuesta de la IA", answer(search.answer(reader, query, authorization)));
    }

    static Component answer(IntranetSearch.Answer answer) {
        if (answer.status() != IntranetSearch.AnswerStatus.ANSWERED) {
            return muted(answer.text());
        }
        var content = new ArrayList<Component>();
        content.add(Text.builder().text(literal(answer.text())).build());
        answer.citations().forEach(c -> content.add(new Anchor("[" + c.number() + "] " + c.title(), Ui.itemLink(c.itemId()))));
        content.add(muted("Generada por IA a partir de contenidos que puedes leer. Compruébalo en las fuentes."));
        return column(content);
    }

    Component results(String language, IntranetSearch.Results results, String query, boolean peopleFound) {
        var content = new ArrayList<Component>();
        if (!results.semantic()) {
            content.add(muted("Ahora mismo solo se busca por palabras: el servicio de significado no responde."));
        }
        if (results.items().isEmpty()) {
            content.add(muted("Nada de lo que puedes ver coincide con «" + literal(query) + "»"
                    + (peopleFound ? ", salvo personas." : ".")));
        } else {
            var names = portal.sectionNames(language);
            results.items().forEach(r -> content.add(result(r, names)));
        }
        return section("Resultados (" + results.items().size() + ")", column(content));
    }

    static Component result(IntranetSearch.Result r, Map<String, String> sections) {
        var i = r.item();
        var about = new ArrayList<String>();
        about.add(ItemType.valueOf(i.type()).label());
        if (i.sectionId() != null && sections.containsKey(i.sectionId())) {
            about.add(sections.get(i.sectionId()));
        }
        about.add(Ui.day(i.publishAt()));
        var lines = new ArrayList<Component>();
        lines.add(new Anchor(i.title(), Ui.itemLink(i.itemId())));
        lines.add(muted(String.join(" · ", about)));
        if (r.caption() != null && !r.caption().isBlank()) {
            lines.add(small(literal(r.caption())));
        }
        return VerticalLayout.builder().spacing(false).padding(false).content(lines).build();
    }

    @Action
    public Object buscar(HttpRequest httpRequest) {
        return this;
    }

    @Action
    public Object preguntar(HttpRequest httpRequest) {
        askedFor = q == null ? null : q.trim();
        return this;
    }

    @Override
    public String toString() {
        return "Buscar";
    }
}
