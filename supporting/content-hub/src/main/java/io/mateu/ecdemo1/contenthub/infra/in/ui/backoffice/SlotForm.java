package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.infra.in.security.ContentAdmins;
import io.mateu.ecdemo1.contenthub.application.usecases.SlotCommands;
import io.mateu.ecdemo1.contenthub.domain.slot.Slot;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.uidl.annotations.EditableOnlyWhenCreating;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.interfaces.HttpRequest;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 * A slot as configuration: the hole an application leaves on a screen — its key, what it is for and what
 * the application draws when nothing is visible. On the control console; what fills it, the variants, is
 * editorial work on the data plane (SlotContentForm).
 */
@Service
@Scope("prototype")
@ContentAdmins
public class SlotForm implements SimpleCrud.CrudForm {

    @NotEmpty
    @EditableOnlyWhenCreating
    @Label("Clave (aplicacion.pantalla.nombre)")
    String id;
    @Label("Descripción")
    String description;
    @Label("Fallback (lo que pinta la aplicación sin variante visible)")
    String fallback;
    @ReadOnly
    @HiddenInCreate
    @Label("Estado")
    String state;

    final SlotCommands commands;
    final Readers readers;

    public SlotForm(SlotCommands commands, Readers readers) {
        this.commands = commands;
        this.readers = readers;
    }

    public SlotForm load(Slot s) {
        id = s.key();
        description = s.description();
        fallback = s.fallback();
        state = s.orphan() ? "Huérfano: ya no aparece en los descriptores"
                : s.variants().isEmpty() ? "Vacío" : s.variants().size() + " variante(s)";
        return this;
    }

    @Override
    public String create(HttpRequest httpRequest) {
        commands.register(readers.of(httpRequest), id, description, fallback);
        return id;
    }

    @Override
    public void save(HttpRequest httpRequest) {
        commands.describe(readers.of(httpRequest), id, description, fallback);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return id == null ? "Nuevo slot" : id;
    }
}
