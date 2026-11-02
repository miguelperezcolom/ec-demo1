package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.NewsletterIssueRepository;
import io.mateu.ecdemo1.contenthub.application.out.NewsletterRepository;
import io.mateu.ecdemo1.contenthub.application.out.SiteRepository;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterIssue;
import io.mateu.ecdemo1.contenthub.domain.site.Site;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
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

/**
 * The newsletters: each a site of its own (identity, audience, sections, pages) and its issues. A row opens
 * the newsletter; its issues are under it, and in «Números».
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Newsletters")
@Editors
public class NewsletterCrud extends SimpleCrud<NewsletterForm, NewsletterForm, NewsletterForm, NoFilters,
        NewsletterCrud.Row, String> {

    public record Row(@Label("Id") String id, @Label("Nombre") String name, @Label("Audiencia") String audience,
                      @Label("Periodicidad") String cadence, @Label("Activa") String active, @Label("Números") int issues,
                      @Label("Último enviado") String lastSent) {
    }

    final ObjectProvider<NewsletterForm> forms;
    final SiteRepository sites;
    final NewsletterRepository newsletters;
    final NewsletterIssueRepository issues;
    final NamedAudiences namedAudiences;

    @Override
    protected Class<NewsletterForm> formClass() {
        return NewsletterForm.class;
    }

    @Override
    public ListingData<Row> search(SearchRequest request, HttpRequest httpRequest) {
        var labels = namedAudiences.labels();
        return all(sites.findAll().stream().filter(s -> s.kind() == Site.Kind.NEWSLETTER).map(s -> {
            var n = newsletters.find(s.id()).orElse(null);
            var all = issues.ofNewsletter(s.id());
            var sent = all.stream().filter(i -> i.status() == NewsletterIssue.Status.SENT).findFirst();
            return new Row(s.id(), s.name(), s.audience().describe(labels),
                    n == null ? "" : n.settings().cadence().describe(), s.active() ? "Sí" : "No", all.size(),
                    sent.map(i -> "nº " + i.number() + " · " + Ui.when(i.sentAt())).orElse(""));
        }).toList(), "Sin newsletters: crea una");
    }

    @Override
    public NewsletterForm view(String id, HttpRequest httpRequest) {
        return forms.getObject().load(id);
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
        throw new IllegalStateException("Las newsletters no se borran: desactívala y deja de enviarse");
    }
}
