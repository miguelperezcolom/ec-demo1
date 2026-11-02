package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.NewsletterIssueRepository;
import io.mateu.ecdemo1.contenthub.application.out.NewsletterTracking;
import io.mateu.ecdemo1.contenthub.application.out.SiteRepository;
import io.mateu.ecdemo1.contenthub.domain.site.Site;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.annotations.Hidden;
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
import java.util.Locale;

/**
 * Every newsletter's issues (números): number, date, status, opens and clicks. The search box narrows by
 * newsletter or subject. «Nuevo» asks which newsletter and opens its next issue, pre-filled.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
@Title("Números de newsletter")
@Editors
public class NewsletterIssuesCrud extends SimpleCrud<IssueForm, IssueForm, IssueForm, NoFilters,
        NewsletterIssuesCrud.Row, String> {

    public static final String ROUTE = "/editorial/numeros";

    public static String route(String issueId) {
        return ROUTE + "/" + issueId;
    }

    public record Row(@Hidden String id, @Label("Newsletter") String newsletter, @Label("Nº") int number,
                      @Label("Fecha") String date, @Label("Asunto") String subject, @Label("Estado") String status,
                      @Label("Aperturas") long opens, @Label("Clics") long clicks) {
    }

    final ObjectProvider<IssueForm> forms;
    final NewsletterIssueRepository issues;
    final SiteRepository sites;
    final NewsletterTracking tracking;

    @Override
    protected Class<IssueForm> formClass() {
        return IssueForm.class;
    }

    @Override
    public ListingData<Row> search(SearchRequest request, HttpRequest httpRequest) {
        var text = request == null || request.searchText() == null ? "" : request.searchText().trim().toLowerCase(Locale.ROOT);
        var names = new java.util.HashMap<String, String>();
        sites.findAll().forEach(s -> names.put(s.id(), s.name()));
        var all = issues.findAll();
        var totals = tracking.totals(all.stream().map(i -> i.id()).toList());
        var rows = all.stream().map(i -> {
            var t = totals.getOrDefault(i.id(), NewsletterTracking.Totals.NONE);
            return new Row(i.id(), names.getOrDefault(i.newsletterId(), i.newsletterId()), i.number(),
                    i.date() == null ? "" : i.date().toString(), i.subject(),
                    i.status().label() + (i.status() == io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterIssue.Status.SCHEDULED
                            ? " · " + Ui.when(i.scheduledAt()) : ""), t.opens(), t.clicks());
        }).filter(r -> text.isEmpty() || (r.newsletter() + " " + r.subject()).toLowerCase(Locale.ROOT).contains(text)).toList();
        return all(rows, "Sin números todavía");
    }

    @Override
    public IssueForm view(String id, HttpRequest httpRequest) {
        return forms.getObject().load(id);
    }

    @Override
    public IssueForm edit(String id, HttpRequest httpRequest) {
        return view(id, httpRequest);
    }

    @Override
    public IssueForm creationForm(HttpRequest httpRequest) {
        return forms.getObject();
    }

    @Override
    public void deleteAllById(List<String> ids, HttpRequest httpRequest) {
        throw new IllegalStateException("Los números no se borran: cancélalo si no debe salir");
    }

    static boolean newsletter(Site s) {
        return s.kind() == Site.Kind.NEWSLETTER;
    }
}
