package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.Collaboration;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import io.mateu.uidl.interfaces.VisibilitySupplier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

/** One comment for an editor: what, who, when, whether it is hidden and by whom; hide it or show it again. */
@Service
@Scope("prototype")
@Editors
public class CommentPage implements Identifiable, VisibilitySupplier {

    @Hidden
    String id;
    @Hidden
    boolean hidden;

    @ReadOnly
    @Label("Contenido")
    String contenido;
    @ReadOnly
    @Label("Autor")
    String autor;
    @ReadOnly
    @Label("Escrito el")
    String escrito;
    @ReadOnly
    @Label("Comentario")
    String comentario;
    @ReadOnly
    @Label("Estado")
    String estado;

    final Collaboration collaboration;
    final ItemRepository items;
    final Readers readers;

    public CommentPage(Collaboration collaboration, ItemRepository items, Readers readers) {
        this.collaboration = collaboration;
        this.items = items;
        this.readers = readers;
    }

    public CommentPage load(String id, HttpRequest httpRequest) {
        this.id = id;
        var key = id.split("~", 2);
        var moderated = key.length < 2 ? null
                : collaboration.moderated(readers.of(httpRequest), key[0], key[1]).orElse(null);
        if (moderated == null) {
            estado = "Este comentario no existe";
            return this;
        }
        var c = moderated.comment();
        contenido = ItemTitles.of(items, moderated.itemId());
        autor = c.authorName() == null || c.authorName().isBlank() ? c.authorId() : c.authorName();
        escrito = Ui.when(c.at());
        comentario = c.text();
        hidden = c.hidden();
        estado = c.hidden() ? "Oculto por " + c.hiddenBy() + " el " + Ui.when(c.hiddenAt()) : "Visible";
        return this;
    }

    String[] key() {
        return id.split("~", 2);
    }

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "¿Ocultar el comentario?",
            confirmationMessage = "Dejará de verse en el portal. Queda registrado quién lo ha ocultado y cuándo, y se puede volver a mostrar.")
    public Object ocultar(HttpRequest httpRequest) {
        collaboration.hide(readers.of(httpRequest), key()[0], key()[1]);
        return List.of(load(id, httpRequest), Message.success("Comentario oculto"));
    }

    @Toolbar
    @Action
    public Object mostrar(HttpRequest httpRequest) {
        collaboration.restore(readers.of(httpRequest), key()[0], key()[1]);
        return List.of(load(id, httpRequest), Message.success("Comentario visible de nuevo"));
    }

    @Override
    public boolean isHidden(String fieldName, HttpRequest httpRequest) {
        return switch (fieldName) {
            case "ocultar" -> hidden;
            case "mostrar" -> !hidden;
            default -> false;
        };
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return "Comentario";
    }
}
