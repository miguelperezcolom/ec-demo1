package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.SlotRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.SlotCommands;
import io.mateu.ecdemo1.contenthub.domain.slot.Slot;
import io.mateu.ecdemo1.contenthub.infra.in.security.Publishers;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.interfaces.HttpRequest;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * What fills a slot: its variants, each with its audience and priority. Editorial work, on the data plane;
 * the slot itself (key, description, fallback) is configured on the control console (SlotForm). Saving
 * publishes the variants — each change recalculates who sees which.
 */
@Service
@Scope("prototype")
@Publishers
public class SlotContentForm implements SimpleCrud.CrudForm {

    @ReadOnly
    @Label("Clave")
    String id;
    @ReadOnly
    @Label("Para qué es")
    String description;
    @ReadOnly
    @Label("Fallback (lo que pinta la aplicación sin variante visible)")
    String fallback;

    @Section("Variantes")
    @Label("Variantes")
    List<VariantForm> variants = new ArrayList<>();

    final SlotCommands commands;
    final SlotRepository slots;
    final Readers readers;

    public SlotContentForm(SlotCommands commands, SlotRepository slots, Readers readers) {
        this.commands = commands;
        this.slots = slots;
        this.readers = readers;
    }

    public SlotContentForm load(Slot s) {
        id = s.key();
        description = s.description();
        fallback = s.fallback();
        variants = new ArrayList<>(s.variants().stream().map(VariantForm::of).toList());
        return this;
    }

    @Override
    public String create(HttpRequest httpRequest) {
        throw new IllegalStateException("Los slots se dan de alta en la consola de control");
    }

    @Override
    public void save(HttpRequest httpRequest) {
        var reader = readers.of(httpRequest);
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
        return id;
    }
}
