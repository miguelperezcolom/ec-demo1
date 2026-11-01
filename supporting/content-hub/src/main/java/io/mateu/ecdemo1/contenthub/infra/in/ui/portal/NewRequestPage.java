package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.usecases.RequestService;
import io.mateu.ecdemo1.contenthub.domain.request.RequestType;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.interfaces.HttpRequest;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/** A new request: what, when, why. It goes to the reader's manager, then to HR. */
@Service
@Scope("prototype")
@Title("Nueva solicitud")
public class NewRequestPage {

    @Label("Qué pides")
    RequestType tipo = RequestType.VACACIONES;
    @Label("Desde")
    @Help("Para vacaciones, obligatorio. Para formación, cuándo empieza el curso.")
    LocalDate desde;
    @Label("Hasta")
    LocalDate hasta;
    @Label("Detalle")
    @Stereotype(FieldStereotype.textarea)
    String detalle;

    final PortalSupport portal;
    final RequestService requests;

    public NewRequestPage(PortalSupport portal, RequestService requests) {
        this.portal = portal;
        this.requests = requests;
    }

    @Toolbar
    @Action
    public Object enviar(HttpRequest httpRequest) {
        var reader = portal.reader(httpRequest);
        if (reader.viewingAs() != null) {
            return Message.warning("Estás viendo la intranet como otro perfil: no se envía nada");
        }
        try {
            var r = requests.create(reader, tipo, desde, hasta == null ? desde : hasta, detalle);
            return Message.success("Solicitud enviada a tu responsable. Síguela en «Mis solicitudes» (" + r.id() + ").");
        } catch (IllegalArgumentException e) {
            return Message.error(e.getMessage());
        }
    }
}
