package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.SlotRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.SlotCommands;
import io.mateu.ecdemo1.contenthub.domain.slot.Slot;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.uidl.annotations.EditableOnlyWhenCreating;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.interfaces.HttpRequest;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * A slot: what the application's descriptor says of it, and its variants. Saving publishes the variants
 * (each change recalculates who sees which).
 */
@Service
@Scope("prototype")
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

    @Section("Variantes")
    @HiddenInCreate
    @Label("Variantes")
    List<VariantForm> variants = new ArrayList<>();

    final SlotCommands commands;
    final SlotRepository slots;
    final Readers readers;

    public SlotForm(SlotCommands commands, SlotRepository slots, Readers readers) {
        this.commands = commands;
        this.slots = slots;
        this.readers = readers;
    }

    public SlotForm load(Slot s) {
        id = s.key();
        description = s.description();
        fallback = s.fallback();
        state = s.orphan() ? "Huérfano: ya no aparece en los descriptores"
                : s.variants().isEmpty() ? "Vacío" : s.variants().size() + " variante(s)";
        variants = new ArrayList<>(s.variants().stream().map(VariantForm::of).toList());
        return this;
    }

    @Override
    public String create(HttpRequest httpRequest) {
        commands.register(readers.of(httpRequest), id, description, fallback);
        return id;
    }

    @Override
    public void save(HttpRequest httpRequest) {
        var reader = readers.of(httpRequest);
        commands.describe(reader, id, description, fallback);
        var keep = new HashSet<String>();
        for (var v : variants == null ? List.<VariantForm>of() : variants) {
            var variant = v.toVariant();
            keep.add(variant.id());
            commands.putVariant(reader, id, variant);
        }
        for (var existing : slots.find(id).orElseThrow().variants()) {
            if (!keep.contains(existing.id())) {
                commands.removeVariant(reader, id, existing.id());
            }
        }
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
