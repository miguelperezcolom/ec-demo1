package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.usecases.RequestService;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.FieldDataType;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.FormField;
import io.mateu.uidl.data.VerticalLayout;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.DetailContentSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * A request to decide: what was asked and its history, a comment for the employee, and Aprobar /
 * Rechazar — which complete the engine's task as the reader (the engine checks it is theirs).
 */
@Service
@Scope("prototype")
public class ApprovalPage implements Identifiable, DetailContentSupplier {

    @Hidden
    String id;
    @Hidden
    String title;
    @Hidden
    String html;

    @Hidden
    String comentario;

    final PortalSupport portal;
    final RequestService requests;

    public ApprovalPage(PortalSupport portal, RequestService requests) {
        this.portal = portal;
        this.requests = requests;
    }

    public ApprovalPage load(String taskId, HttpRequest httpRequest) {
        portal.reader(httpRequest);
        this.id = taskId;
        var approval = approval(httpRequest);
        if (approval == null) {
            title = "Ya no está pendiente";
            html = "<p>Esa tarea ya está decidida, o no es tuya.</p>";
            return this;
        }
        title = approval.request().type().label() + " · " + approval.request().employeeName();
        html = (approval.hr() ? "<p>Aprobada por su responsable: te toca como <b>RRHH</b>.</p>" : "")
                + RequestHtml.summary(approval.request()) + RequestHtml.history(approval.request());
        return this;
    }

    RequestService.Approval approval(HttpRequest httpRequest) {
        return requests.approvals(httpRequest.getHeaderValue("Authorization")).stream()
                .filter(a -> a.task().id().equals(id)).findFirst().orElse(null);
    }

    @Toolbar
    @Action
    public Object aprobar(HttpRequest httpRequest) {
        return decide(httpRequest, true);
    }

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "¿Rechazar la solicitud?",
            confirmationMessage = "Se le comunica al empleado, con tu comentario.")
    public Object rechazar(HttpRequest httpRequest) {
        return decide(httpRequest, false);
    }

    Object decide(HttpRequest httpRequest, boolean approve) {
        var approval = approval(httpRequest);
        if (approval == null) {
            return Message.warning("Esa tarea ya está decidida, o no es tuya");
        }
        try {
            requests.decide(httpRequest.getHeaderValue("Authorization"), approval, approve, comentario);
        } catch (IllegalStateException e) {
            return Message.error(e.getMessage());
        }
        // the page again, now saying it is no longer pending (Aprobar twice would find no task)
        load(id, httpRequest);
        return List.of(this, Message.success(approve ? (approval.hr() ? "Aprobada. Se le comunica al empleado."
                : "Aprobada: pasa a RRHH.") : "Rechazada. Se le comunica al empleado."));
    }

    /**
     * The request and an editable comment: the listing opens this as its record's page, which Mateu
     * draws read-only from the fields — the comment has to be a field the reader can type in.
     */
    @Override
    public Component detailContent(HttpRequest httpRequest) {
        return VerticalLayout.builder().fullWidth(true).content(List.of(
                Html.block(html == null ? "" : html),
                FormField.builder().id("comentario").label("Comentario para el empleado")
                        .dataType(FieldDataType.string).stereotype(FieldStereotype.textarea).build())).build();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return title == null ? "Por aprobar" : title;
    }
}
