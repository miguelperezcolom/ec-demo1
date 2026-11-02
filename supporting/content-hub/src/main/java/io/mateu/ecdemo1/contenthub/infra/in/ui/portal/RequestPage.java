package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.usecases.RequestService;
import io.mateu.uidl.data.EmptyState;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.fluent.Form;
import io.mateu.uidl.interfaces.ComponentTreeSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

/** One of the reader's requests: what, where it is, and what happened to it. Only their own. */
@Service
@Scope("prototype")
public class RequestPage implements ComponentTreeSupplier, Identifiable {

    String id;

    final PortalSupport portal;
    final RequestService requests;

    public RequestPage(PortalSupport portal, RequestService requests) {
        this.portal = portal;
        this.requests = requests;
    }

    public RequestPage load(String id, HttpRequest httpRequest) {
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
        var reader = portal.reader(httpRequest);
        var r = requests.find(id).filter(x -> x.employeeId().equals(reader.userId())).orElse(null);
        if (r == null) {
            return Form.builder().title("No encontrada")
                    .content(List.of(EmptyState.builder().title("Esa solicitud no existe o no es tuya").build()))
                    .build();
        }
        return Form.builder().title(RequestComponents.title(r)).subtitle(RequestComponents.subtitle(r))
                .badge(RequestComponents.status(r))
                .content(RequestComponents.sections(r))
                .build();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return "Solicitud";
    }
}
