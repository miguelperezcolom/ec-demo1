package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.usecases.RequestService;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;

/**
 * A request to decide: what was asked and its history, a comment for the employee, and Aprobar /
 * Rechazar — which complete the engine's task as the reader (the engine checks it is theirs).
 */
@Service
@Scope("prototype")
public class ApprovalPage implements Identifiable {

    @Hidden
    String id;
    @Hidden
    String title;
    @Hidden
    String html;

    @Label("")
    @Colspan(2)
    Callable<Component> content = () -> Html.block(html == null ? "" : html);

    @Label("Comentario para el empleado")
    @Stereotype(FieldStereotype.textarea)
    @Colspan(2)
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
        return Message.success(approve ? (approval.hr() ? "Aprobada. Se le comunica al empleado."
                : "Aprobada: pasa a RRHH.") : "Rechazada. Se le comunica al empleado.");
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
