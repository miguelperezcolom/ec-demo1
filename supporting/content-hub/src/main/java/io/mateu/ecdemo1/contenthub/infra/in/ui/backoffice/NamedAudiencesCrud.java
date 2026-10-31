package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.NamedAudienceRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
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

@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Audiencias con nombre")
public class NamedAudiencesCrud extends SimpleCrud<NamedAudienceForm, NamedAudienceForm, NamedAudienceForm, NoFilters,
        NamedAudiencesCrud.Row, String> {

    public record Row(@Label("Id") String id, @Label("Nombre") String name, @Label("Expresión") String expression,
                      @Label("Empleados") long employees) {
    }

    final ObjectProvider<NamedAudienceForm> forms;
    final NamedAudienceRepository repository;
    final AudienceCommands commands;
    final NamedAudiences named;
    final Readers readers;

    @Override
    protected Class<NamedAudienceForm> formClass() {
        return NamedAudienceForm.class;
    }

    @Override
    public ListingData<Row> search(SearchRequest request, HttpRequest httpRequest) {
        var labels = named.labels();
        return all(repository.findAll().stream().map(a -> new Row(a.id(), a.name(), a.expression().describe(labels),
                commands.count(a.expression()))).toList(), "Sin audiencias con nombre");
    }

    @Override
    public NamedAudienceForm view(String id, HttpRequest httpRequest) {
        return forms.getObject().load(repository.find(id).orElseThrow());
    }

    @Override
    public NamedAudienceForm edit(String id, HttpRequest httpRequest) {
        return view(id, httpRequest);
    }

    @Override
    public NamedAudienceForm creationForm(HttpRequest httpRequest) {
        return forms.getObject();
    }

    @Override
    public void deleteAllById(List<String> ids, HttpRequest httpRequest) {
        var reader = readers.of(httpRequest);
        ids.forEach(id -> commands.delete(reader, id));
    }
}
