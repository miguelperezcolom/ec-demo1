package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.out.NewsletterIssueRepository;
import io.mateu.ecdemo1.contenthub.application.out.SiteRepository;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.RowRoute;
import io.mateu.uidl.annotations.Title;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.SearchRequest;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Listing;
import io.mateu.uidl.interfaces.Navigable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 * The editable newsletter issues, to edit one on the issue itself ({@link IssueVisualEditorPage},
 * /editorial/editorNumeros/{id}). An issue's form opens the same route with «Editar sobre el número».
 */
@Service
@Scope("prototype")
@Title("Editor visual de números")
@RowRoute("/editorial/editorNumeros/${row.id}")
@Editors
public class IssueVisualEditorListing implements Listing<IssueVisualEditorListing.Row>, Navigable<IssueVisualEditorPage, String> {

    public record Row(@Label("Id") String id, @Label("Newsletter") String newsletter, @Label("Nº") int number,
                      @Label("Asunto") String subject, @Label("Estado") String status) {
    }

    final NewsletterIssueRepository issues;
    final SiteRepository sites;
    final ObjectProvider<IssueVisualEditorPage> pages;

    public IssueVisualEditorListing(NewsletterIssueRepository issues, SiteRepository sites,
                                    ObjectProvider<IssueVisualEditorPage> pages) {
        this.issues = issues;
        this.sites = sites;
        this.pages = pages;
    }

    @Override
    public ListingData<Row> search(SearchRequest request, HttpRequest httpRequest) {
        var names = new java.util.HashMap<String, String>();
        sites.findAll().forEach(s -> names.put(s.id(), s.name()));
        var rows = issues.findAll().stream().filter(i -> i.editable())
                .map(i -> new Row(i.id(), names.getOrDefault(i.newsletterId(), i.newsletterId()), i.number(), i.subject(),
                        i.status().label())).toList();
        return new ListingData<>(new Page<>("", Math.max(rows.size(), 1), 0, rows.size(), rows),
                "Ningún número en borrador o programado");
    }

    @Override
    public IssueVisualEditorPage view(String id, HttpRequest httpRequest) {
        return pages.getObject().load(id);
    }
}
