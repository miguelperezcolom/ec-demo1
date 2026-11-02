package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.UiSources;
import io.mateu.ecdemo1.contenthub.application.usecases.UiSourceCommands;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.infra.in.security.ContentAdmins;
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

/**
 * The registry of external UIs (configuration): which UIs editors may put in the intranet — as a page, as
 * a block, as a menu entry — and who may use each. Removing one hides what uses it.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("UIs externas")
@ContentAdmins
public class UiSourcesCrud extends SimpleCrud<UiSourceForm, UiSourceForm, UiSourceForm, NoFilters, UiSourcesCrud.Row, String> {

    public record Row(@Label("Id") String id, @Label("Nombre") String name, @Label("Tipo") String kind,
                      @Label("Dónde está") String where, @Label("Quién la usa") String who,
                      @Label("Activa") String active) {
    }

    final ObjectProvider<UiSourceForm> forms;
    final UiSources sources;
    final UiSourceCommands commands;
    final NamedAudiences named;
    final Readers readers;

    @Override
    protected Class<UiSourceForm> formClass() {
        return UiSourceForm.class;
    }

    @Override
    public ListingData<Row> search(SearchRequest request, HttpRequest httpRequest) {
        var labels = named.labels();
        return all(sources.all().stream().map(s -> new Row(s.id(), s.name(), s.kind().label(),
                s.basePath() + (s.tagName() == null ? "" : " <" + s.tagName() + ">"),
                s.audience().describe(labels) + (s.requiredScopes().isEmpty() ? "" : " · con " + String.join(" ", s.requiredScopes())),
                s.active() ? "Sí" : "No")).toList(), "Sin UIs externas registradas");
    }

    @Override
    public UiSourceForm view(String id, HttpRequest httpRequest) {
        return forms.getObject().load(sources.find(id).orElseThrow());
    }

    @Override
    public UiSourceForm edit(String id, HttpRequest httpRequest) {
        return view(id, httpRequest);
    }

    @Override
    public UiSourceForm creationForm(HttpRequest httpRequest) {
        return forms.getObject();
    }

    @Override
    public void deleteAllById(List<String> ids, HttpRequest httpRequest) {
        var reader = readers.of(httpRequest);
        ids.forEach(id -> commands.delete(reader, id));
    }
}
