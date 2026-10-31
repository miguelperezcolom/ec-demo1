package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.MediaRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.MediaCommands;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.NoFilters;
import io.mateu.uidl.data.Page;
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
@Title("Biblioteca de medios")
public class MediaCrud extends SimpleCrud<MediaForm, MediaForm, MediaForm, NoFilters, MediaCrud.Row, String> {

    public record Row(@Label("Id") String id, @Label("Título") String title, @Label("Tipo") String kind,
                      @Label("Para incrustar") String mark, @Label("Subido") String uploadedAt) {
    }

    final ObjectProvider<MediaForm> forms;
    final MediaRepository media;
    final MediaCommands commands;
    final Readers readers;

    @Override
    protected Class<MediaForm> formClass() {
        return MediaForm.class;
    }

    @Override
    public ListingData<Row> search(SearchRequest request, HttpRequest httpRequest) {
        var text = request == null ? "" : request.searchText();
        var rows = media.search(text, 0, 200).stream().map(a -> new Row(a.id(), a.title(), a.kind().label(),
                "[[media:" + a.id() + "]]", Ui.when(a.uploadedAt()))).toList();
        return new ListingData<>(new Page<>(text == null ? "" : text, 200, 0, media.count(text), rows), "La biblioteca está vacía");
    }

    @Override
    public MediaForm view(String id, HttpRequest httpRequest) {
        return forms.getObject().load(media.find(id).orElseThrow());
    }

    @Override
    public MediaForm edit(String id, HttpRequest httpRequest) {
        return view(id, httpRequest);
    }

    @Override
    public MediaForm creationForm(HttpRequest httpRequest) {
        return forms.getObject();
    }

    @Override
    public void deleteAllById(List<String> ids, HttpRequest httpRequest) {
        var reader = readers.of(httpRequest);
        ids.forEach(id -> commands.delete(reader, id));
    }
}
