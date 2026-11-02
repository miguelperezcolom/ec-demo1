package io.mateu.ecdemo1.contenthub.infra.in.web;

import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.out.SiteRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.NewsletterEditorial;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.newsletter.Newsletter;
import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterIssue;
import io.mateu.ecdemo1.contenthub.domain.site.Site;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.uidl.UserFacingException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * The visual editor ({@code <ec-visual-editor resource="issues">}) on a newsletter ISSUE: the same component
 * and the same wire as on a page (VisualEditorController) — its blocks, a preview as the editor or as a
 * profile, the save — over the issue's blocks. A newsletter section block draws the items the issue carries
 * in that section; a block's audience narrows the newsletter's.
 */
@RestController
@RequestMapping("/_intranet/api/editor/issues")
@RequiredArgsConstructor
public class IssueEditorController {

    final VisualEditorController pages;
    final NewsletterEditorial editorial;
    final SiteRepository sites;
    final NavigationRepository navigation;
    final ItemRepository items;
    final AudienceCommands audiences;
    final NamedAudiences namedAudiences;

    @GetMapping("/{id}")
    public Map<String, Object> issue(@PathVariable String id, HttpServletRequest request) {
        var reader = pages.editor(request);
        return payload(reader, issue(id));
    }

    @PostMapping("/{id}/preview")
    public Map<String, Object> preview(@PathVariable String id, @RequestBody VisualEditorController.BlocksRequest body,
                                       HttpServletRequest request) {
        var reader = pages.editor(request);
        var issue = issue(id);
        var site = site(issue);
        var blocks = pages.blocks(body);
        var out = new LinkedHashMap<String, Object>();
        Reader as = reader;
        if (body.viewAs() != null && !body.viewAs().isBlank()) {
            var profile = audiences.allProfiles().get(body.viewAs());
            if (profile == null) {
                throw new IllegalArgumentException("Ese perfil ya no está en el directorio");
            }
            as = new Reader(reader.userId(), reader.name(), profile.attributes(), reader.permissions(), profile.hash());
            out.put("profile", VisualEditorController.profileLabel(profile));
            out.put("pageVisible", namedAudiences.policy().admits(site.audience(), profile.attributes()));
        }
        out.put("blocks", render(as, issue, site, blocks, as != reader));
        out.put("media", pages.mediaOf(blocks));
        out.put("warnings", List.of());
        return out;
    }

    @PutMapping("/{id}/blocks")
    public Map<String, Object> save(@PathVariable String id, @RequestBody VisualEditorController.BlocksRequest body,
                                    HttpServletRequest request) {
        var reader = pages.editor(request);
        try {
            editorial.editIssueBlocks(reader, id, pages.blocks(body), body.updatedAt());
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Alguien")) {
                throw new VisualEditorController.Conflict(e.getMessage());
            }
            throw e;
        }
        var payload = payload(reader, issue(id));
        payload.put("saved", true);
        return payload;
    }

    Map<String, Object> payload(Reader reader, NewsletterIssue issue) {
        var site = site(issue);
        var out = new LinkedHashMap<String, Object>();
        var page = new LinkedHashMap<String, Object>();
        page.put("id", issue.id());
        page.put("title", site.name() + " · nº " + issue.number() + " — " + issue.subject());
        page.put("language", site.definition().languages().isEmpty() ? "es" : site.definition().languages().getFirst());
        page.put("status", issue.status().label());
        page.put("published", issue.status() == NewsletterIssue.Status.SENT);
        page.put("updatedAt", issue.updatedAt() == null ? null : issue.updatedAt().toString());
        page.put("audience", site.audience().describe(namedAudiences.labels()));
        page.put("count", audiences.count(site.audience()));
        page.put("canEdit", reader.may(EditorialPermission.EDIT) && issue.editable());
        out.put("page", page);
        out.put("blocks", issue.blocks().stream().map(VisualEditorController::dto).toList());
        out.put("rendered", render(reader, issue, site, issue.blocks(), false));
        out.put("media", pages.mediaOf(issue.blocks()));
        out.put("warnings", issue.editable() ? List.of() : List.of("Este número está " + issue.status().label().toLowerCase()
                + ": ya no se cambia"));
        var catalog = pages.catalog();
        var sections = new ArrayList<Map<String, String>>(VisualEditorController.options(
                io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice.Catalog.get().sections()));
        navigation.get(site.id()).sections().forEach(s -> sections.add(Map.of("value", Newsletter.SECTION_PREFIX + s.id(),
                "label", site.name() + " · " + s.name("es"))));
        catalog.put("sections", sections);
        out.put("catalog", catalog);
        out.put("profiles", audiences.allProfiles().values().stream()
                .map(p -> Map.of("value", p.hash(), "label", VisualEditorController.profileLabel(p))).toList());
        return out;
    }

    /** As on a page; a newsletter section block, the titles of the items the issue carries in it. */
    List<VisualEditorController.RenderedBlock> render(Reader reader, NewsletterIssue issue, Site site, List<Block> blocks,
                                                      boolean asProfile) {
        var rendered = pages.render(reader, site.audience(), blocks, asProfile);
        var names = new LinkedHashMap<String, String>();
        navigation.get(site.id()).sections().forEach(s -> names.put(s.id(), s.name("es")));
        var out = new ArrayList<VisualEditorController.RenderedBlock>();
        for (int i = 0; i < blocks.size(); i++) {
            var b = blocks.get(i);
            var r = rendered.get(i);
            if (b.type() == BlockType.NEWSLETTER_SECTION && r.visible()) {
                var key = Newsletter.sectionOf(b);
                var title = b.title() == null || b.title().isBlank() ? names.getOrDefault(key, key) : b.title();
                var list = issue.section(key).stream().map(itemId -> items.find(itemId).map(it ->
                        "<li><a href=\"" + Ui.itemLink(itemId) + "\">" + Ui.esc(it.draft().in(it.defaultLanguage(),
                                it.defaultLanguage()).title()) + "</a></li>").orElse("")).reduce("", String::concat);
                var html = "<section style=\"margin:1rem 0;padding:1rem 1.25rem;border:1px dashed rgba(128,128,128,.45);"
                        + "border-radius:12px\"><h3 style=\"margin:0 0 .5rem\">" + Ui.esc(title) + "</h3>"
                        + (list.isEmpty() ? "<p style=\"opacity:.7\">Sin items en esta sección (añádelos en el formulario del número)</p>"
                        : "<ul>" + list + "</ul>") + "</section>";
                r = new VisualEditorController.RenderedBlock(r.id(), html, true, r.audience(), r.count());
            }
            out.add(r);
        }
        return out;
    }

    NewsletterIssue issue(String id) {
        return editorial.findIssue(id).orElseThrow(() -> new NoSuchElementException("No existe el número " + id));
    }

    Site site(NewsletterIssue issue) {
        return sites.find(issue.newsletterId()).orElseThrow();
    }

    @ExceptionHandler(SecurityException.class)
    ResponseEntity<Map<String, String>> forbidden(SecurityException e) {
        return VisualEditorController.error(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(UserFacingException.class)
    ResponseEntity<Map<String, String>> refused(UserFacingException e) {
        return VisualEditorController.error(e.getCause() instanceof SecurityException ? HttpStatus.FORBIDDEN
                : HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> invalid(RuntimeException e) {
        return VisualEditorController.error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String, String>> missing(NoSuchElementException e) {
        return VisualEditorController.error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(VisualEditorController.Conflict.class)
    ResponseEntity<Map<String, String>> conflict(VisualEditorController.Conflict e) {
        return VisualEditorController.error(HttpStatus.CONFLICT, e.getMessage());
    }
}
