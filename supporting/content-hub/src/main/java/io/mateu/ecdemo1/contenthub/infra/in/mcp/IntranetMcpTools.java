package io.mateu.ecdemo1.contenthub.infra.in.mcp;

import io.mateu.ecdemo1.contenthub.application.out.ContentQuery;
import io.mateu.ecdemo1.contenthub.application.usecases.HtmlSanitizer;
import io.mateu.ecdemo1.contenthub.application.usecases.ReaderActions;
import io.mateu.ecdemo1.contenthub.application.visibility.ContentQuestions;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * The intranet for the chat agent, with the token of the person chatting (ia-agent forwards it, the
 * security chain verified it): every tool goes through the same visibility function as the portal, so
 * the agent only ever knows what that person may read.
 */
@Component
@RequiredArgsConstructor
public class IntranetMcpTools {

    final Readers readers;
    final Visibility visibility;
    final ReaderActions actions;
    final ContentQuestions questions;

    public record Found(String id, String title, String type, String date, String summary, String link) {
    }

    public record Passage(String itemId, String title, String text, String link) {
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

    Reader reader() {
        var request = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a ? a.getRequest() : null;
        return readers.real(h -> request == null ? null : request.getHeader(h));
    }
}
