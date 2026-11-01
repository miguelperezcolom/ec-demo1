package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.usecases.RequestService;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;

/** One of the reader's requests: what, where it is, and what happened to it. Only their own. */
@Service
@Scope("prototype")
public class RequestPage implements Identifiable {

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
    final RequestService requests;

    public RequestPage(PortalSupport portal, RequestService requests) {
        this.portal = portal;
        this.requests = requests;
    }

    public RequestPage load(String id, HttpRequest httpRequest) {
        var reader = portal.reader(httpRequest);
        this.id = id;
        var r = requests.find(id).filter(x -> x.employeeId().equals(reader.userId())).orElse(null);
        if (r == null) {
            title = "No encontrada";
            html = "<p>Esa solicitud no existe o no es tuya.</p>";
            return this;
        }
        title = r.type().label();
        html = RequestHtml.summary(r) + RequestHtml.history(r);
        return this;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return title == null ? "Solicitud" : title;
    }
}
