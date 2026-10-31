package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.infra.in.security.ContentAdmins;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.NavigationCommands;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
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

/** The navigation tree. A section with subsections is not removed. */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Secciones")
@ContentAdmins
public class SectionsCrud extends SimpleCrud<SectionForm, SectionForm, SectionForm, NoFilters, SectionsCrud.Row, String> {

    public record Row(@Label("Id") String id, @Label("Sección") String name, @Label("Posición") int position) {
    }

    final ObjectProvider<SectionForm> forms;
    final NavigationRepository navigation;
    final NavigationCommands commands;
    final Readers readers;

    @Override
    protected Class<SectionForm> formClass() {
        return SectionForm.class;
    }

    @Override
    public ListingData<Row> search(SearchRequest request, HttpRequest httpRequest) {
        var nav = navigation.get();
        return all(nav.sections().stream().map(s -> new Row(s.id(),
                "— ".repeat(nav.ancestors(s.id()).size()) + s.name("es"), s.order())).toList(), "Sin secciones");
    }

    @Override
    public SectionForm view(String id, HttpRequest httpRequest) {
        return forms.getObject().load(navigation.get().find(id).orElseThrow());
    }

    @Override
    public SectionForm edit(String id, HttpRequest httpRequest) {
        return view(id, httpRequest);
    }

    @Override
    public SectionForm creationForm(HttpRequest httpRequest) {
        return forms.getObject();
    }

    @Override
    public void deleteAllById(List<String> ids, HttpRequest httpRequest) {
        var reader = readers.of(httpRequest);
        ids.forEach(id -> commands.remove(reader, id));
    }
}
