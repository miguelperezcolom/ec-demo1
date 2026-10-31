package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.infra.in.security.ContentAdmins;
import io.mateu.ecdemo1.contenthub.application.out.NewsletterRepository;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
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
@Title("Newsletter")
@ContentAdmins
public class NewsletterCrud extends SimpleCrud<NewsletterForm, NewsletterForm, NewsletterForm, NoFilters,
        NewsletterCrud.Row, String> {

    public record Row(@Label("Id") String id, @Label("Título") String title, @Label("Estado") String status,
                      @Label("Envío") String scheduledAt, @Label("Items") int items, @Label("Grupos enviados") long groups) {
    }

    final ObjectProvider<NewsletterForm> forms;
    final NewsletterRepository editions;

    @Override
    protected Class<NewsletterForm> formClass() {
        return NewsletterForm.class;
    }

    @Override
    public ListingData<Row> search(SearchRequest request, HttpRequest httpRequest) {
        return all(editions.findAll().stream().map(e -> new Row(e.id(), e.title(), switch (e.status()) {
            case DRAFT -> "Borrador";
            case SCHEDULED -> "Programada";
            case SENT -> "Enviada";
        }, Ui.when(e.scheduledAt()), e.itemIds().size(), e.sends().stream().filter(s -> s.sentAt() != null).count()))
                .toList(), "Sin ediciones");
    }

    @Override
    public NewsletterForm view(String id, HttpRequest httpRequest) {
        return forms.getObject().load(editions.find(id).orElseThrow());
    }

    @Override
    public NewsletterForm edit(String id, HttpRequest httpRequest) {
        return view(id, httpRequest);
    }

    @Override
    public NewsletterForm creationForm(HttpRequest httpRequest) {
        return forms.getObject();
    }

    @Override
    public void deleteAllById(List<String> ids, HttpRequest httpRequest) {
        throw new IllegalStateException("Las ediciones no se borran");
    }
}
