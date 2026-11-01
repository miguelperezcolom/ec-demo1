package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
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
@Editors
public class NewsletterCrud extends SimpleCrud<NewsletterForm, NewsletterForm, NewsletterForm, NoFilters,
        NewsletterCrud.Row, String> {

    public record Row(@Label("Id") String id, @Label("Título") String title, @Label("Estado") String status,
                      @Label("Envío") String scheduledAt, @Label("Items") int items, @Label("Grupos enviados") long groups) {
    }

    final ObjectProvider<NewsletterForm> forms;
    final NewsletterRepository editions;
    final io.mateu.ecdemo1.contenthub.application.usecases.WeeklyNewsletter weekly;
    final io.mateu.ecdemo1.contenthub.infra.in.security.Readers readers;
    final java.time.Clock clock;

    /** This week's edition now, instead of waiting for Monday: a draft to review. */
    @io.mateu.uidl.annotations.Toolbar
    @io.mateu.uidl.annotations.Action
    public Object prepararBoletinDeLaSemana(HttpRequest httpRequest) {
        readers.of(httpRequest).require(io.mateu.ecdemo1.contenthub.application.security.EditorialPermission.EDIT);
        return weekly.compose(clock.instant(), false)
                .<Object>map(id -> io.mateu.uidl.data.Message.success("Preparado el borrador " + id + ": revísalo y prográmalo"))
                .orElseGet(() -> io.mateu.uidl.data.Message.warning("Ya existe el boletín de esta semana, o no hay nada nuevo que contar"));
    }

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
