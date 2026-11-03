package io.mateu.ecdemo1.contenthub.infra.in.mcp;

import io.mateu.ecdemo1.contenthub.application.out.VisibleItem;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.HtmlSanitizer;
import io.mateu.ecdemo1.contenthub.application.usecases.NewsletterService;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterIssue;
import io.mateu.ecdemo1.contenthub.domain.site.Site;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The newsletters, read-only, for whoever calls the MCP with their token — the same server as the intranet's
 * tools. Only what the portal would show them: the newsletters whose audience admits them, and of those the
 * SENT issues (a draft or a scheduled issue is the editorial backoffice's, never here, whatever the caller's
 * permissions), each drawn as they get it (NewsletterService.read: their blocks, their items, their sections).
 * Subscribing stays in the portal. The links are absolute (the portal's URL), as in the email: the caller may
 * be a desktop client, outside the portal.
 */
@Component
public class NewsletterMcpTools {

    static final int MAX_ISSUES = 50;

    final NewsletterService newsletters;
    final Supplier<Reader> caller;

    @Autowired
    public NewsletterMcpTools(NewsletterService newsletters, McpCaller caller) {
        this(newsletters, caller::reader);
    }

    public NewsletterMcpTools(NewsletterService newsletters, Supplier<Reader> caller) {
        this.newsletters = newsletters;
        this.caller = caller;
    }

    public record IssueSummary(String id, String newsletter, int number, String subject, String date, String sentAt,
                               String link) {
    }

    /**
     * A newsletter the user may read.
     *
     * @param sections the sections they receive when subscribed (empty: all)
     */
    public record NewsletterSummary(String id, String name, String description, boolean subscribed,
                                    List<String> sections, IssueSummary latestIssue, String link) {
    }

    public record IssueItem(String id, String title, String type, String date, String summary, String link) {
    }

    public record IssueLink(String label, String url) {
    }

    /** One block of the issue as the user gets it: its text, the items it lists, its links. */
    public record IssueBlock(String type, String title, String text, List<IssueItem> items, List<IssueLink> links) {
    }

    public record IssueText(String id, String newsletter, String newsletterName, int number, String subject, String date,
                            String sentAt, List<IssueBlock> blocks, String link) {
    }

    @Tool(description = "The newsletters the user may read (those whose audience includes them): whether they receive each by email, which sections, and its latest sent issue")
    public List<NewsletterSummary> mis_newsletters() {
        return newsletters.newslettersOf(caller.get()).stream()
                .map(s -> new NewsletterSummary(s.site().id(), s.site().name(), s.site().definition().description(),
                        s.preference().subscribed(), List.copyOf(s.preference().sections()),
                        s.latest() == null ? null : summary(s.latest()), newsletters.siteUrl(s.site().id())))
                .toList();
    }

    @Tool(description = "The sent issues of a newsletter the user may read, the latest first (at most 50); drafts and scheduled issues are not included")
    public List<IssueSummary> numeros_newsletter(@ToolParam(description = "The newsletter id, as mis_newsletters returns it") String newsletter) {
        var site = newsletters.readable(caller.get(), newsletter);
        if (site.isEmpty()) {
            return List.of();
        }
        return newsletters.archive(site.get().id()).stream().limit(MAX_ISSUES).map(this::summary).toList();
    }

    @Tool(description = "A sent issue of a newsletter as the user receives it: subject, date, its blocks as text with the items they can see, and its web link")
    public IssueText leer_numero(
            @ToolParam(description = "The newsletter id, as mis_newsletters returns it") String newsletter,
            @ToolParam(description = "The issue id, as numeros_newsletter returns it; empty for the latest sent issue", required = false) String numero) {
        var reader = caller.get();
        var site = newsletters.readable(reader, newsletter).orElse(null);
        Optional<NewsletterIssue> issue = site == null ? Optional.empty() : numero == null || numero.isBlank()
                ? newsletters.latestSent(site.id()) : newsletters.readableIssue(reader, site.id(), numero.trim());
        if (issue.isEmpty()) {
            return new IssueText(numero, newsletter, site == null ? "" : site.name(), 0,
                    "No existe, no ha salido todavía o no es para ti", "", "", List.of(), "");
        }
        var i = issue.get();
        var blocks = newsletters.read(reader, i).stream().map(b -> block(site, b)).toList();
        return new IssueText(i.id(), site.id(), site.name(), i.number(), i.subject(), date(i), Ui.day(i.sentAt()), blocks,
                newsletters.permalink(i));
    }

    IssueBlock block(Site site, NewsletterService.ReadBlock b) {
        var text = switch (b.type()) {
            case HTML, BANNER -> HtmlSanitizer.text(b.html().replaceAll("\\[\\[media:[^\\]]*]]", "[medio: ver en la versión web]"));
            case MEDIA -> "[medio: ver en la versión web]";
            case REMOTE -> "[contenido interactivo: ver en la versión web]";
            default -> "";
        };
        return new IssueBlock(label(b.type()), b.title(), text, b.items().stream().map(item -> item(site, item)).toList(),
                b.links().stream().map(l -> new IssueLink(l.label(), l.url())).toList());
    }

    static String label(BlockType type) {
        return type == BlockType.NEWSLETTER_SECTION ? "Sección" : type.label();
    }

    IssueItem item(Site site, VisibleItem i) {
        return new IssueItem(i.itemId(), i.title(), i.type(), Ui.day(i.publishAt()), i.summary(),
                NewsletterService.itemPath(newsletters.portalUrl(), i, site.id(), id -> newsletters.isOwnItem(site.id(), id)));
    }

    IssueSummary summary(NewsletterIssue i) {
        return new IssueSummary(i.id(), i.newsletterId(), i.number(), i.subject(), date(i), Ui.day(i.sentAt()),
                newsletters.permalink(i));
    }

    static String date(NewsletterIssue i) {
        return i.date() == null ? "" : i.date().toString();
    }
}
