package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.usecases.NewsletterEditorial;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.Element;
import io.mateu.uidl.data.UICommand;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * A newsletter issue edited on the issue itself: the same {@code <ec-visual-editor>} as a page's
 * (VisualEditorPage), pointed at the issue ({@code resource="issues"}, IssueEditorController).
 */
@Service
@Scope("prototype")
@Editors
public class IssueVisualEditorPage implements Identifiable {

    @Hidden
    String id;
    @Hidden
    String title;

    @Label("")
    @Colspan(2)
    Callable<Component> editor = this::editor;

    final NewsletterEditorial editorial;

    public IssueVisualEditorPage(NewsletterEditorial editorial) {
        this.editorial = editorial;
    }

    public IssueVisualEditorPage load(String id) {
        var issue = editorial.issue(id);
        this.id = id;
        this.title = "nº " + issue.number() + " · " + issue.subject();
        return this;
    }

    Component editor() {
        return new Element("ec-visual-editor", Map.of("page-id", id == null ? "" : id, "resource", "issues",
                "api", VisualEditorPage.API, "import", "/_intranet/embed.js"), Map.of(), "", "display:block;width:100%", "",
                false);
    }

    /** Subject, items, schedule and stats stay in the issue's form. */
    @Toolbar
    @Action
    public Object volverAlNumero(HttpRequest httpRequest) {
        return UICommand.navigateTo(NewsletterIssuesCrud.route(id));
    }

    public static String route(String id) {
        return "/editorial/editorNumeros/" + id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return "Editor visual · " + (title == null ? id : title);
    }
}
