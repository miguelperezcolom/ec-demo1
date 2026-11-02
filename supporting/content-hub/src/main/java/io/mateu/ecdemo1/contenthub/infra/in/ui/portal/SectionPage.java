package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.out.ContentQuery;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.uidl.data.EmptyState;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.fluent.Form;
import io.mateu.uidl.interfaces.ComponentTreeSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/** A section's visible content, newest first. */
@Service
@Scope("prototype")
public class SectionPage implements ComponentTreeSupplier, Identifiable {

    String id;

    final PortalSupport portal;
    final NavigationRepository navigation;

    public SectionPage(PortalSupport portal, NavigationRepository navigation) {
        this.portal = portal;
        this.navigation = navigation;
    }

    public SectionPage load(String id, HttpRequest httpRequest) {
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
        var language = reader.attributes().language();
        var title = navigation.get().find(id).map(s -> s.name(language)).orElse(id);
        var items = portal.latest(reader, ContentQuery.section(id, 0, 50));
        var content = new ArrayList<Component>(PortalComponents.viewingAs(reader.viewingAs()));
        content.add(items.isEmpty()
                ? EmptyState.builder().title("No hay nada aquí que puedas ver").build()
                : PageRenderer.section(null, PortalComponents.items(items, "")));
        return Form.builder().title(title)
                .subtitle(items.size() + (items.size() == 1 ? " contenido" : " contenidos"))
                .content(content).build();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return "Sección";
    }
}
