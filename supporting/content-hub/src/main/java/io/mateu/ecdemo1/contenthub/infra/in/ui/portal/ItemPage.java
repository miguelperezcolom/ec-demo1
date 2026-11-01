package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.usecases.ReaderActions;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import io.mateu.uidl.interfaces.VisibilitySupplier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;

/**
 * An item as a reader sees it — only reachable when the visibility function says they may. Opening it
 * records the read (for items with read tracking); the toolbar stars it and confirms it.
 */
@Service
@Scope("prototype")
public class ItemPage implements Identifiable, VisibilitySupplier {

    @Hidden
    String id;
    @Hidden
    String title;
    @Hidden
    String html;
    @Hidden
    boolean confirmable;
    @Hidden
    boolean favorite;

    @Label("")
    @Colspan(2)
    Callable<Component> content = () -> Html.block(html == null ? "" : html);

    final PortalSupport portal;
    final PageRenderer renderer;
    final ReaderActions actions;

    public ItemPage(PortalSupport portal, PageRenderer renderer, ReaderActions actions) {
        this.portal = portal;
        this.renderer = renderer;
        this.actions = actions;
    }

    public ItemPage load(String id, HttpRequest httpRequest) {
        var reader = portal.reader(httpRequest);
        var item = portal.item(reader, id).orElse(null);
        this.id = id;
        if (item == null) {
            title = "No disponible";
            html = "<p>Este contenido no existe o no lo puedes ver.</p>";
            return this;
        }
        title = item.title();
        html = banner(reader.viewingAs()) + renderer.item(reader, item, portal.sectionNames(reader.attributes().language()));
        confirmable = item.confirmationRequired() && reader.viewingAs() == null && !actions.hasConfirmed(reader, item);
        favorite = actions.isFavorite(reader, id);
        actions.recordRead(reader, item);
        return this;
    }

    static String banner(String viewingAs) {
        return viewingAs == null ? "" : "<div style=\"background:#fff3cd;color:#664d03;padding:.5rem .75rem;border-radius:8px;"
                + "margin-bottom:1rem\">👁 Estás viendo la intranet como el perfil <b>" + viewingAs
                + "</b> (vista previa del backoffice).</div>";
    }

    @Toolbar
    @Action
    public Object favorito(HttpRequest httpRequest) {
        var now = actions.toggleFavorite(portal.reader(httpRequest), id);
        favorite = now;
        return Message.success(now ? "Añadido a tus favoritos" : "Quitado de tus favoritos");
    }

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "¿Confirmas que lo has leído?",
            confirmationMessage = "Quedará registrado con tu nombre y la versión que has leído.")
    public Object confirmarLectura(HttpRequest httpRequest) {
        actions.confirm(portal.reader(httpRequest), id);
        confirmable = false;
        return Message.success("Lectura confirmada. ¡Gracias!");
    }

    @Override
    public boolean isHidden(String fieldName, HttpRequest httpRequest) {
        return "confirmarLectura".equals(fieldName) && !confirmable;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return title == null ? "Contenido" : title;
    }
}
