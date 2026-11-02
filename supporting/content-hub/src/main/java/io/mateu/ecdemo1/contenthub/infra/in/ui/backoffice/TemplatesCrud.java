package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.usecases.Templates;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.infra.in.security.Publishers;
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
import java.util.stream.Collectors;

/**
 * Page templates, kept by publishers (content:publish or content:admin): what every editor starts a page
 * from with «Nueva página desde plantilla». Deleting one changes no page made from it.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Plantillas de página")
@Publishers
public class TemplatesCrud extends SimpleCrud<TemplateForm, TemplateForm, TemplateForm, NoFilters, TemplatesCrud.Row, String> {

    public record Row(@Label("Id") String id, @Label("Nombre") String name, @Label("Para qué sirve") String description,
                      @Label("Bloques") String blocks, @Label("Audiencia por defecto") String audience,
                      @Label("Sitio") String site) {
    }

    final ObjectProvider<TemplateForm> forms;
    final Templates templates;
    final NamedAudiences named;
    final Readers readers;

    @Override
    protected Class<TemplateForm> formClass() {
        return TemplateForm.class;
    }

    @Override
    public ListingData<Row> search(SearchRequest request, HttpRequest httpRequest) {
        var labels = named.labels();
        return all(templates.all().stream().map(t -> new Row(t.id(), t.name(), t.description(),
                t.blocks().stream().map(Block::type).map(b -> b.label()).collect(Collectors.joining(", ")),
                t.defaultAudience() == null ? "Todos" : t.defaultAudience().describe(labels),
                t.siteId() == null ? "Todos" : t.siteId())).toList(),
                "Sin plantillas: crea una, o guarda una página como plantilla desde su formulario");
    }

    @Override
    public TemplateForm view(String id, HttpRequest httpRequest) {
        return forms.getObject().load(templates.find(id).orElseThrow());
    }

    @Override
    public TemplateForm edit(String id, HttpRequest httpRequest) {
        return view(id, httpRequest);
    }

    @Override
    public TemplateForm creationForm(HttpRequest httpRequest) {
        return forms.getObject();
    }

    @Override
    public void deleteAllById(List<String> ids, HttpRequest httpRequest) {
        var reader = readers.of(httpRequest);
        ids.forEach(id -> templates.delete(reader, id));
    }

    public static String route(String id) {
        return "/editorial/plantillas/" + id;
    }
}
