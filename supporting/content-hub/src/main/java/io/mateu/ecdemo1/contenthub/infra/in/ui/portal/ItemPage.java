package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.usecases.Collaboration;
import io.mateu.ecdemo1.contenthub.application.usecases.ItemNotifications;
import io.mateu.ecdemo1.contenthub.application.usecases.ReaderActions;
import io.mateu.ecdemo1.contenthub.application.usecases.UsageTracker;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.data.Anchor;
import io.mateu.uidl.data.Button;
import io.mateu.uidl.data.ButtonStyle;
import io.mateu.uidl.data.Comment;
import io.mateu.uidl.data.CommentThread;
import io.mateu.uidl.data.EmptyState;
import io.mateu.uidl.data.FieldDataType;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.FormField;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.MultimediaContentPage;
import io.mateu.uidl.data.Notice;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.fluent.Form;
import io.mateu.uidl.interfaces.ComponentTreeSupplier;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PageRenderer.html;
import static io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PageRenderer.section;

/**
 * An item as a reader sees it — only reachable when the visibility function says they may — on
 * Mateu's multimedia content page template (Redwood draws Spectra's): a banner with the title, what
 * it is and when, and the summary, its actions (star it, like it, confirm having read it) as the
 * banner's calls to action; under it the body, the medium or link it points at and, for a page, its
 * blocks, each a section; last, its comments and the box to add one (unless an editor closed them).
 * Opening it records the read (for items with read tracking).
 */
@Service
@Scope("prototype")
public class ItemPage implements ComponentTreeSupplier, Identifiable {

    String id;

    /** The comment being written (the box under the comments). */
    String comentario;

    final PortalSupport portal;
    final PageRenderer renderer;
    final ReaderActions actions;
    final Collaboration collaboration;
    final UsageTracker usage;
    final ItemNotifications notifications;

    public ItemPage(PortalSupport portal, PageRenderer renderer, ReaderActions actions, Collaboration collaboration,
                    UsageTracker usage, ItemNotifications notifications) {
        this.portal = portal;
        this.renderer = renderer;
        this.actions = actions;
        this.collaboration = collaboration;
        this.usage = usage;
        this.notifications = notifications;
    }

    public ItemPage load(String id, HttpRequest httpRequest) {
        this.id = id;
        return this;
    }

    @Override
    public Component component(HttpRequest httpRequest) {
        if (id == null) {
            id = httpRequest.lastPathItem();
        }
        var reader = portal.reader(httpRequest);
        var item = portal.item(reader, id).orElse(null);
        if (item == null) {
            return Form.builder().title("No disponible")
                    .content(List.of(EmptyState.builder().title("Este contenido no existe o no lo puedes ver")
                            .description("Puede que haya caducado o que no esté dirigido a tu perfil.").build()))
                    .build();
        }
        actions.recordRead(reader, item);
        if (PortalSupport.opening(httpRequest)) {
            usage.itemViewed(reader, item.itemId());
            notifications.opened(reader, item.itemId());
        }
        var content = new ArrayList<Component>(PortalComponents.viewingAs(reader.viewingAs()));
        var confirmable = item.confirmationRequired() && reader.viewingAs() == null && !actions.hasConfirmed(reader, item);
        if (confirmable) {
            content.add(Notice.builder().theme("warning").text("Este contenido requiere tu confirmación de lectura"
                    + (item.confirmationDeadline() == null ? "" : " antes del " + Ui.day(item.confirmationDeadline()))
                    + ". Usa «Confirmar lectura».").build());
        }
        // the body as the editor wrote it, in an element: a read-only html field sanitises it on Redwood,
        // dropping the embeds' placeholders (<div data-ec-embed>) that embed.js turns into players
        var body = renderer.body(reader, item);
        if (!PageRenderer.blank(body)) {
            content.add(section(null, html("body", body)));
        }
        var medium = renderer.medium(reader, item);
        if (medium != null) {
            content.add(section(null, html("medium", medium)));
        } else if (item.url() != null && !item.url().isBlank()) {
            content.add(section(null, new Anchor(ItemType.DOCUMENT.name().equals(item.type()) ? "Abrir el documento"
                    : "Abrir el enlace", item.url(), "_blank", "", "")));
        }
        if (ItemType.PAGE.name().equals(item.type())) {
            content.addAll(renderer.sections(reader, item.itemId()));
        }
        var social = collaboration.social(reader, id);
        content.add(comments(reader, social));
        var toolbar = new ArrayList<io.mateu.uidl.fluent.UserTrigger>();
        if (confirmable) {
            toolbar.add(Button.builder().label("Confirmar lectura").actionId("confirmarLectura")
                    .buttonStyle(ButtonStyle.primary).build());
        }
        toolbar.add(Button.builder().label(actions.isFavorite(reader, id) ? "Quitar de favoritos" : "Añadir a favoritos")
                .actionId("favorito").build());
        if (reader.viewingAs() == null) {
            toolbar.add(Button.builder().label((social.liked() ? "Ya no me gusta" : "Me gusta")
                    + " (" + social.likes() + ")").actionId("meGusta").build());
        }
        return Form.builder()
                .title(item.title())
                .overline(PortalComponents.about(item, portal.sectionNames(reader.attributes().language())))
                .subtitle(item.summary())
                .template(new MultimediaContentPage(PortalComponents.TONE.name()))
                .toolbar(toolbar)
                .content(content)
                .build();
    }

    /**
     * The comments readers see (hidden ones are not), with Mateu's comment thread, and the box to add
     * one — unless the editors closed the item to comments, or this is an editor's «ver como».
     */
    Component comments(io.mateu.ecdemo1.contenthub.application.security.Reader reader, Collaboration.Social social) {
        var list = social.comments();
        var parts = new ArrayList<Component>();
        if (list.isEmpty()) {
            parts.add(PortalComponents.muted("Todavía no hay comentarios."));
        } else {
            parts.add(CommentThread.builder().id("comentarios").comments(list.stream().map(c -> {
                var author = c.authorName() == null || c.authorName().isBlank() ? c.authorId() : c.authorName();
                return new Comment(c.id(), author, People.initials(author), c.text(), Ui.when(c.at()), List.of());
            }).toList()).build());
        }
        if (!social.open()) {
            parts.add(PortalComponents.muted("Los comentarios de este contenido están cerrados."));
        } else if (reader.viewingAs() == null) {
            parts.add(FormField.builder().id("comentario").label("Tu comentario").dataType(FieldDataType.string)
                    .stereotype(FieldStereotype.textarea).placeholder("Escribe un comentario para tus compañeros")
                    .build());
            parts.add(Button.builder().label("Comentar").actionId("comentar").buttonStyle(ButtonStyle.secondary).build());
        }
        return section("Comentarios (" + list.size() + ")", PortalComponents.column(parts));
    }

    @Action
    public Object comentar(HttpRequest httpRequest) {
        var text = comentario;
        if ((text == null || text.isBlank()) && httpRequest.runActionRq() != null
                && httpRequest.runActionRq().componentState() != null) {
            text = (String) httpRequest.runActionRq().componentState().get("comentario");
        }
        // a refusal (empty, too long, closed, not visible) reaches the reader as a message: UserFacingErrors
        collaboration.comment(portal.reader(httpRequest), id, text);
        comentario = null;
        return List.of(this, Message.success("Comentario publicado"));
    }

    @Action
    public Object meGusta(HttpRequest httpRequest) {
        var now = collaboration.toggleLike(portal.reader(httpRequest), id);
        return List.of(this, Message.success(now ? "Te gusta" : "Ya no te gusta"));
    }

    @Action
    public Object favorito(HttpRequest httpRequest) {
        var now = actions.toggleFavorite(portal.reader(httpRequest), id);
        return Message.success(now ? "Añadido a tus favoritos" : "Quitado de tus favoritos");
    }

    @Action(confirmationRequired = true, confirmationTitle = "¿Confirmas que lo has leído?",
            confirmationMessage = "Quedará registrado con tu nombre y la versión que has leído.")
    public Object confirmarLectura(HttpRequest httpRequest) {
        var reader = portal.reader(httpRequest);
        actions.confirm(reader, id);
        notifications.confirmed(reader, id);
        return Message.success("Lectura confirmada. ¡Gracias!");
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return "Contenido";
    }
}
