package io.mateu.ecdemo1.contenthub.infra.in.mcp;

import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import io.mateu.ecdemo1.contenthub.infra.in.ui.portal.People;

import io.mateu.ecdemo1.contenthub.application.out.ContentQuery;
import io.mateu.ecdemo1.contenthub.application.usecases.HtmlSanitizer;
import io.mateu.ecdemo1.contenthub.application.usecases.ReaderActions;
import io.mateu.ecdemo1.contenthub.application.visibility.ContentQuestions;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The intranet for the chat agent, with the token of the person chatting (ia-agent forwards it, the
 * security chain verified it): every tool goes through the same visibility function as the portal, so
 * the agent only ever knows what that person may read.
 */
@Component
@RequiredArgsConstructor
public class IntranetMcpTools {

    final McpCaller caller;
    final Visibility visibility;
    final ReaderActions actions;
    final ContentQuestions questions;
    final People people;

    public record Found(String id, String title, String type, String date, String summary, String link) {
    }

    public record Passage(String itemId, String title, String text, String link) {
    }

    /** A person as the directory shows them: work data only. */
    public record Person(String id, String name, String jobTitle, String department, String hotel, String region,
                         String email, String phone, String reportsTo, String link) {
    }

    /** A person's card: them, the chain of managers above them (nearest first) and who reports to them. */
    public record PersonCard(Person person, List<Person> managers, List<Person> directReports, List<Person> team) {
    }

    public record ItemText(String id, String title, String date, String text, String link, boolean mustConfirm) {
    }

    public String getSystemContext() {
        return """
                Intranet de comunicación interna (content-hub):
                - Noticias, avisos, páginas, documentos y enlaces para los empleados. Cada empleado ve solo lo que
                  su perfil (empresa, país, región, hotel, departamento, puesto) admite: estas herramientas ya
                  devuelven solo lo que la persona que chatea puede ver. No supongas que existe nada más.
                - Para responder preguntas sobre el contenido usa preguntar_intranet (búsqueda semántica: devuelve
                  los fragmentos más relevantes) y cita siempre los items con su enlace.
                - buscar_contenido busca por palabras; leer_item da el texto completo de un item.
                - confirmaciones_pendientes: lo que la persona debe confirmar haber leído y aún no ha confirmado.
                  Confirmar se hace en el portal, no desde el chat.
                - Directorio de personas (organigrama): buscar_personas encuentra empleados por nombre, puesto,
                  departamento, hotel o región ("jefe recepción tenerife"); ficha_persona da su ficha con su
                  cadena de responsables y su equipo. Solo datos de trabajo; enlaza siempre la ficha.
                - Newsletters: mis_newsletters lista las que son para la persona (si la recibe, último número);
                  numeros_newsletter da los números ya enviados de una; leer_numero da un número tal como esa
                  persona lo recibe (sus bloques, solo lo que puede ver) con su enlace web. Suscribirse o darse de
                  baja se hace en el portal, no desde el chat.
                """;
    }

    @Tool(description = "Semantic search over the intranet content the user can see: the passages most relevant to a question, to answer it citing the items (RAG)")
    public List<Passage> preguntar_intranet(@ToolParam(description = "The question, in the user's words") String pregunta) {
        var reader = reader();
        return questions.passagesFor(reader.attributes(), pregunta, 6).stream()
                .map(p -> new Passage(p.itemId(), p.title(), p.text(), Ui.itemLink(p.itemId()))).toList();
    }

    @Tool(description = "Full-text search of the intranet content the user can see (news, notices, pages, documents); words match by prefix")
    public List<Found> buscar_contenido(@ToolParam(description = "Words to look for; empty for the latest") String texto) {
        var reader = reader();
        var query = texto == null || texto.isBlank() ? ContentQuery.latest(10) : ContentQuery.search(texto, 0, 10);
        return visibility.visibles(reader.attributes(), query).content().stream()
                .map(i -> new Found(i.itemId(), i.title(), i.type(), Ui.day(i.publishAt()), i.summary(), Ui.itemLink(i.itemId())))
                .toList();
    }

    @Tool(description = "The full text of one intranet item, if the user can see it")
    public ItemText leer_item(@ToolParam(description = "The item id") String id) {
        var reader = reader();
        return visibility.item(reader.attributes(), id)
                .map(i -> new ItemText(i.itemId(), i.title(), Ui.day(i.publishAt()),
                        i.summary() + "\n\n" + HtmlSanitizer.text(i.body()), Ui.itemLink(i.itemId()),
                        i.confirmationRequired() && !actions.hasConfirmed(reader, i)))
                .orElse(new ItemText(id, "No existe o no lo puedes ver", "", "", "", false));
    }

    @Tool(description = "Intranet items the user must confirm having read and has not confirmed yet")
    public List<Found> confirmaciones_pendientes() {
        return actions.pendingConfirmations(reader()).stream()
                .map(i -> new Found(i.itemId(), i.title(), i.type(), Ui.day(i.publishAt()), i.summary(), Ui.itemLink(i.itemId())))
                .toList();
    }

    @Tool(description = "Find people in the company directory by name, job title, department, hotel or region (all the words must match, accents ignored), e.g. 'jefe recepcion tenerife'")
    public List<Person> buscar_personas(@ToolParam(description = "Words to look for") String texto) {
        reader();
        return people.search(texto).stream().limit(20).map(this::person).toList();
    }

    @Tool(description = "A person's directory card: their job, where they work, contact, the chain of managers above them and the people who report to them")
    public PersonCard ficha_persona(@ToolParam(description = "The person's user id, as buscar_personas returns it") String id) {
        reader();
        return people.find(id).map(e -> new PersonCard(person(e), people.chainOf(e).stream().map(this::person).toList(),
                        people.reportsOf(e.userId()).stream().map(this::person).toList(),
                        people.teamOf(e).stream().map(this::person).toList()))
                .orElse(null);
    }

    Person person(Employee e) {
        var manager = e.managerId() == null ? null : people.find(e.managerId()).map(Employee::name).orElse(null);
        return new Person(e.userId(), e.name(), people.jobTitle(e), people.department(e),
                people.hotel(e).isEmpty() ? "Servicios centrales" : people.hotel(e), people.region(e), e.email(),
                e.phone(), manager, People.link(e.userId()));
    }

    Reader reader() {
        return caller.reader();
    }
}
