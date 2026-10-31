package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.infra.in.security.ContentAdmins;
import io.mateu.ecdemo1.contenthub.application.out.SlotRepository;
import io.mateu.uidl.annotations.Label;
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
 * The slots registered by application and screen, with their state — configuration, on the control
 * console. Orphans are kept, not deleted. Their variants are filled on the data plane (SlotContentCrud).
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Slots de contenido")
@ContentAdmins
public class SlotsCrud extends SimpleCrud<SlotForm, SlotForm, SlotForm, NoFilters, SlotsCrud.Row, String> {

    public record Row(@Label("Clave") String id, @Label("Aplicación") String application,
                      @Label("Pantalla") String screen, @Label("Estado") String state) {
    }

    final ObjectProvider<SlotForm> forms;
    final SlotRepository slots;

    @Override
    protected Class<SlotForm> formClass() {
        return SlotForm.class;
    }

    @Override
    public ListingData<Row> search(SearchRequest request, HttpRequest httpRequest) {
        return all(slots.findAll().stream().map(s -> new Row(s.key(), s.application(), s.screen(),
                s.orphan() ? "Huérfano" : s.variants().isEmpty() ? "Vacío" : s.variants().size() + " variante(s)"))
                .toList(), "Ningún slot registrado");
    }

    @Override
    public SlotForm view(String id, HttpRequest httpRequest) {
        return forms.getObject().load(slots.find(id).orElseThrow());
    }

    @Override
    public SlotForm edit(String id, HttpRequest httpRequest) {
        return view(id, httpRequest);
    }

    @Override
    public SlotForm creationForm(HttpRequest httpRequest) {
        return forms.getObject();
    }

    @Override
    public void deleteAllById(List<String> ids, HttpRequest httpRequest) {
        throw new IllegalStateException("Los slots no se borran: se marcan huérfanos cuando desaparecen de los descriptores");
    }
}
