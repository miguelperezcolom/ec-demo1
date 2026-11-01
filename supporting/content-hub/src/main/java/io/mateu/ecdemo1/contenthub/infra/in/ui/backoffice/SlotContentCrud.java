package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.SlotRepository;
import io.mateu.ecdemo1.contenthub.infra.in.security.Publishers;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.NotCreatable;
import io.mateu.uidl.annotations.NotDeletable;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.NoFilters;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The slots' content: the live slots (orphans left out) and how many variants fill each. The slots are
 * registered and described on the control console; here they are only filled.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Contenido de slots")
@Publishers
@NotCreatable
@NotDeletable
public class SlotContentCrud extends SimpleCrud<SlotContentForm, SlotContentForm, SlotContentForm, NoFilters,
        SlotContentCrud.Row, String> {

    public record Row(@Label("Clave") String id, @Label("Para qué es") String description,
                      @Label("Variantes") String variants) {
    }

    final ObjectProvider<SlotContentForm> forms;
    final SlotRepository slots;

    @Override
    protected Class<SlotContentForm> formClass() {
        return SlotContentForm.class;
    }

    @Override
    public ListingData<Row> search(SearchRequest request, HttpRequest httpRequest) {
        return all(slots.findAll().stream().filter(s -> !s.orphan())
                .map(s -> new Row(s.key(), s.description(),
                        s.variants().isEmpty() ? "Ninguna: se ve el fallback" : String.valueOf(s.variants().size())))
                .toList(), "Ningún slot registrado");
    }

    @Override
    public SlotContentForm view(String id, HttpRequest httpRequest) {
        return forms.getObject().load(slots.find(id).orElseThrow());
    }

    @Override
    public SlotContentForm edit(String id, HttpRequest httpRequest) {
        return view(id, httpRequest);
    }

    @Override
    public SlotContentForm creationForm(HttpRequest httpRequest) {
        throw new IllegalStateException("Los slots se dan de alta en la consola de control");
    }

    @Override
    public void deleteAllById(List<String> ids, HttpRequest httpRequest) {
        throw new IllegalStateException("Los slots no se borran: se marcan huérfanos cuando desaparecen de los descriptores");
    }
}
