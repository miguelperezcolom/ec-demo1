package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.application.drive.DriveDocuments;
import io.mateu.ecdemo1.contenthub.application.usecases.ReaderActions;
import io.mateu.ecdemo1.contenthub.domain.drive.DriveFileRef;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.data.Anchor;
import io.mateu.uidl.data.Button;
import io.mateu.uidl.data.ButtonStyle;
import io.mateu.uidl.data.EmptyState;
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
 * it is and when, and the summary, its actions (star it, confirm having read it) as the banner's
 * calls to action; under it the body, the medium or link it points at and, for a page, its blocks,
 * each a section. Opening it records the read (for items with read tracking).
 */
@Service
@Scope("prototype")
public class ItemPage implements ComponentTreeSupplier, Identifiable {

    String id;

    final PortalSupport portal;
    final PageRenderer renderer;
    final ReaderActions actions;
    final DriveDocuments drive;

    public ItemPage(PortalSupport portal, PageRenderer renderer, ReaderActions actions, DriveDocuments drive) {
        this.portal = portal;
        this.renderer = renderer;
        this.actions = actions;
        this.drive = drive;
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
        var driveUrl = DriveFileRef.parse(item.url());
        if (medium != null) {
            content.add(section(null, html("medium", medium)));
        } else if (driveUrl.isPresent()) {
            // a reference, not a copy: Google's live preview of the one version, in Drive
            content.add(section(null, html("medium", renderer.drive(driveUrl.get(), item.title()))));
        } else if (item.url() != null && !item.url().isBlank()) {
            content.add(section(null, new Anchor(ItemType.DOCUMENT.name().equals(item.type()) ? "Abrir el documento"
                    : "Abrir el enlace", item.url(), "_blank", "", "")));
        }
        drive.ofItem(item).forEach(d -> content.add(section(d.name(), PortalComponents.driveFile(d))));
        if (ItemType.PAGE.name().equals(item.type())) {
            content.addAll(renderer.sections(reader, item.itemId()));
        }
        var toolbar = new ArrayList<io.mateu.uidl.fluent.UserTrigger>();
        if (confirmable) {
            toolbar.add(Button.builder().label("Confirmar lectura").actionId("confirmarLectura")
                    .buttonStyle(ButtonStyle.primary).build());
        }
        toolbar.add(Button.builder().label(actions.isFavorite(reader, id) ? "Quitar de favoritos" : "Añadir a favoritos")
                .actionId("favorito").build());
        return Form.builder()
                .title(item.title())
                .overline(PortalComponents.about(item, portal.sectionNames(reader.attributes().language())))
                .subtitle(item.summary())
                .template(new MultimediaContentPage(PortalComponents.TONE.name()))
                .toolbar(toolbar)
                .content(content)
                .build();
    }

    @Action
    public Object favorito(HttpRequest httpRequest) {
        var now = actions.toggleFavorite(portal.reader(httpRequest), id);
        return Message.success(now ? "Añadido a tus favoritos" : "Quitado de tus favoritos");
    }

    @Action(confirmationRequired = true, confirmationTitle = "¿Confirmas que lo has leído?",
            confirmationMessage = "Quedará registrado con tu nombre y la versión que has leído.")
    public Object confirmarLectura(HttpRequest httpRequest) {
        actions.confirm(portal.reader(httpRequest), id);
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
