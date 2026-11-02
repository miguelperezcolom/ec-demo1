package io.mateu.ecdemo1.contenthub.infra.in.web;

import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.MediaRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.ItemCommands;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemStatus;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.media.MediaAsset;
import io.mateu.ecdemo1.contenthub.domain.media.MediaKind;
import io.mateu.ecdemo1.contenthub.domain.media.MediaReferences;
import io.mateu.ecdemo1.contenthub.infra.in.render.ContentRenderer;
import io.mateu.ecdemo1.contenthub.infra.in.render.MediaUrls;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice.BlockForm;
import io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice.Catalog;
import io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice.LinkForm;
import io.mateu.ecdemo1.contenthub.infra.in.ui.portal.PageRenderer;
import io.mateu.uidl.UserFacingException;
import io.mateu.uidl.data.Option;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * The visual editor's API ({@code <ec-visual-editor>}, static/visual-editor.js): a page's DRAFT blocks with
 * the HTML the portal would draw for each, a preview of unsaved blocks — as the editor, or "as" a profile of
 * the directory — and the save, which replaces the draft's blocks through {@link ItemCommands#editBlocks}.
 *
 * <p>Authorised exactly like the backoffice's screens: the caller comes from {@link Readers} (the token the
 * gateway verified and its {@code X-User-Scopes}); reading needs an editorial permission, as {@code @Editors}
 * does, and saving {@code content:edit}, as the form's save does. The gateway routes {@code /_intranet/**}
 * only on the data plane's hosts and only signed in — not on the intranet's own host. A block's audience
 * comes as departments / hotels / job titles of the directory only: it narrows the page's, never widens it.
 */
@RestController
@RequestMapping("/_intranet/api/editor")
@RequiredArgsConstructor
public class VisualEditorController {

    final Readers readers;
    final ItemRepository items;
    final ItemCommands commands;
    final AudienceCommands audiences;
    final NamedAudiences namedAudiences;
    final PageRenderer renderer;
    final ContentRenderer content;
    final MediaRepository media;
    final MediaUrls urls;
    final Catalog catalog;

    // ── the wire ────────────────────────────────────────────────────────────────────────────────────

    /** A block as the editor edits it: the fields of {@link BlockForm}, its audience as three lists. */
    public record BlockDto(String id, String type, String title, String html, String itemId, String sectionId,
                           Integer limit, List<LinkDto> links, String mediaId, List<String> departments,
                           List<String> hotels, List<String> jobTitles) {
    }

    public record LinkDto(String label, String url) {
    }

    /** What the editor sends to preview or save: the blocks in order, and — to save — the version it started from. */
    public record BlocksRequest(List<BlockDto> blocks, String viewAs, String updatedAt) {
    }

    /** One block as drawn: its HTML, whether the profile sees it, and who that is. */
    public record RenderedBlock(String id, String html, boolean visible, String audience, long count) {
    }

    // ── endpoints ───────────────────────────────────────────────────────────────────────────────────

    /** The editor's code: for editors only, fetched with their token (see embed.js). */
    @GetMapping(value = "/visual-editor.js", produces = "text/javascript")
    public ResponseEntity<Resource> script(HttpServletRequest request) {
        editor(request);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/javascript"))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(new ClassPathResource("static/visual-editor.js"));
    }

    @GetMapping("/pages/{id}")
    public Map<String, Object> page(@PathVariable String id, HttpServletRequest request) {
        var reader = editor(request);
        return pagePayload(reader, page(id));
    }

    /** Draws blocks that are not saved, as the editor or as a profile ({@code viewAs}: a profile's hash). */
    @PostMapping("/pages/{id}/preview")
    public Map<String, Object> preview(@PathVariable String id, @RequestBody BlocksRequest body, HttpServletRequest request) {
        var reader = editor(request);
        var item = page(id);
        var blocks = blocks(body);
        var out = new LinkedHashMap<String, Object>();
        Reader as = reader;
        if (body.viewAs() != null && !body.viewAs().isBlank()) {
            var profile = audiences.allProfiles().get(body.viewAs());
            if (profile == null) {
                throw new IllegalArgumentException("Ese perfil ya no está en el directorio");
            }
            as = new Reader(reader.userId(), reader.name(), profile.attributes(), reader.permissions(), profile.hash());
            out.put("profile", profileLabel(profile));
            out.put("pageVisible", namedAudiences.policy().admits(item.draft().audience(), profile.attributes()));
        }
        out.put("blocks", render(as, item.draft().audience(), blocks, as != reader));
        out.put("media", mediaOf(blocks));
        out.put("warnings", commands.warnings(withBlocks(item.draft(), blocks)));
        return out;
    }

    @PutMapping("/pages/{id}/blocks")
    public Map<String, Object> save(@PathVariable String id, @RequestBody BlocksRequest body, HttpServletRequest request) {
        var reader = editor(request);
        var item = page(id);
        if (body.updatedAt() != null && item.updatedAt() != null && !body.updatedAt().equals(item.updatedAt().toString())) {
            throw new Conflict("Alguien ha cambiado esta página desde que la abriste. Recarga para ver sus cambios "
                    + "(perderás los tuyos) o cópialos antes.");
        }
        commands.editBlocks(reader, id, blocks(body));
        var payload = pagePayload(reader, page(id));
        payload.put("saved", true);
        return payload;
    }

    /** A medium of the library as it is drawn inside a text — for "Insertar medio". */
    @GetMapping("/media/{mediaId}")
    public Map<String, Object> medium(@PathVariable String mediaId, HttpServletRequest request) {
        editor(request);
        var asset = media.find(mediaId).orElseThrow(() -> new NoSuchElementException("No existe el medio " + mediaId));
        return Map.of("id", asset.id(), "html", content.medium(asset, PageRenderer.PORTAL_BASE, MediaUrls.EDITOR));
    }

    // ── payloads ────────────────────────────────────────────────────────────────────────────────────

    Map<String, Object> pagePayload(Reader reader, Item item) {
        var draft = item.draft();
        var t = draft.in(item.defaultLanguage(), item.defaultLanguage());
        var out = new LinkedHashMap<String, Object>();
        var page = new LinkedHashMap<String, Object>();
        page.put("id", item.id());
        page.put("title", t.title());
        page.put("language", item.defaultLanguage());
        page.put("status", item.status().label() + (item.draftInReview() ? " · cambios en revisión"
                : item.hasVersions() && item.draftChanged() ? " · cambios sin publicar" : ""));
        page.put("published", item.status() == ItemStatus.PUBLISHED);
        page.put("updatedAt", item.updatedAt() == null ? null : item.updatedAt().toString());
        page.put("audience", draft.audience().describe(namedAudiences.labels()));
        page.put("count", audiences.count(draft.audience()));
        page.put("canEdit", reader.may(EditorialPermission.EDIT));
        out.put("page", page);
        out.put("blocks", draft.blocks().stream().map(VisualEditorController::dto).toList());
        out.put("rendered", render(reader, draft.audience(), draft.blocks(), false));
        out.put("media", mediaOf(draft.blocks()));
        out.put("warnings", commands.warnings(draft));
        out.put("catalog", catalog());
        out.put("profiles", audiences.allProfiles().values().stream()
                .map(p -> Map.of("value", p.hash(), "label", profileLabel(p))).toList());
        return out;
    }

    List<RenderedBlock> render(Reader reader, Audience pageAudience, List<Block> blocks, boolean asProfile) {
        var policy = namedAudiences.policy();
        var labels = namedAudiences.labels();
        var out = new ArrayList<RenderedBlock>();
        for (var b : blocks) {
            var effective = Audience.effective(pageAudience, b.audience());
            var visible = !asProfile || policy.admits(effective, reader.attributes());
            var own = b.audience() instanceof Audience.Everyone ? "" : b.audience().describe(labels);
            out.add(new RenderedBlock(b.id(), visible ? renderer.draftHtml(reader, b) : "", visible, own,
                    audiences.count(effective)));
        }
        return out;
    }

    /** The media marked in the blocks' texts, as drawn — the editor shows them in place of their marks. */
    Map<String, String> mediaOf(List<Block> blocks) {
        var ids = new LinkedHashSet<String>();
        for (var b : blocks) {
            if (b.html() != null) {
                var m = MediaReferences.MARK.matcher(b.html());
                while (m.find()) {
                    ids.add(m.group(1));
                }
            }
        }
        var out = new LinkedHashMap<String, String>();
        ids.forEach(id -> out.put(id, content.medium(id, PageRenderer.PORTAL_BASE, MediaUrls.EDITOR)));
        return out;
    }

    Map<String, Object> catalog() {
        var out = new LinkedHashMap<String, Object>();
        out.put("types", Arrays.stream(BlockType.values()).map(t -> Map.of("value", t.name(), "label", t.label(),
                "fields", BlockForm.fieldsOf(t))).toList());
        out.put("items", options(catalog.publishedItems()));
        out.put("sections", options(catalog.sections()));
        out.put("media", media.search("", 0, 200).stream().map(this::mediaOption).toList());
        out.put("departments", options(catalog.values(Attribute.DEPARTMENT)));
        out.put("hotels", options(catalog.values(Attribute.HOTEL)));
        out.put("jobTitles", options(catalog.values(Attribute.JOB_TITLE)));
        return out;
    }

    Map<String, Object> mediaOption(MediaAsset a) {
        var out = new LinkedHashMap<String, Object>();
        out.put("value", a.id());
        out.put("label", a.title());
        out.put("kind", a.kind().name().toLowerCase());
        String thumb = null;
        if (a.kind() == MediaKind.IMAGE) {
            thumb = urls.url(PageRenderer.PORTAL_BASE, a.id(), MediaUrls.EDITOR);
        } else if (a.kind() == MediaKind.EXTERNAL) {
            thumb = a.external().thumbnail().orElse(null);
        }
        out.put("thumb", thumb);
        return out;
    }

    static List<Map<String, String>> options(List<Option> options) {
        return options.stream().map(o -> Map.of("value", String.valueOf(o.value()), "label", String.valueOf(o.label()))).toList();
    }

    static String profileLabel(AudienceCommands.Profile p) {
        return p.attributes().canonical().replace(";", " · ") + " — " + p.employees() + " empleado(s)";
    }

    // ── conversions ─────────────────────────────────────────────────────────────────────────────────

    static BlockDto dto(Block b) {
        var f = BlockForm.of(b);
        return new BlockDto(b.id(), b.type().name(), b.title(), b.html(), b.itemId(), b.sectionId(), b.limit(),
                b.links().stream().map(l -> new LinkDto(l.label(), l.url())).toList(), b.mediaId(),
                nonNull(f.blockDepartments), nonNull(f.blockHotels), nonNull(f.blockJobTitles));
    }

    /**
     * The blocks as {@link BlockForm} turns them into the domain's — the form's own conversion — after
     * checking what the form's selects would have: a known type, and audience values the directory has.
     */
    List<Block> blocks(BlocksRequest body) {
        if (body == null || body.blocks() == null) {
            throw new IllegalArgumentException("Faltan los bloques");
        }
        var known = new LinkedHashMap<Attribute, Set<String>>();
        for (var attribute : Block.NARROWING) {
            var values = new HashSet<String>();
            catalog.values(attribute).forEach(o -> values.add(String.valueOf(o.value())));
            known.put(attribute, values);
        }
        var out = new ArrayList<Block>();
        for (var d : body.blocks()) {
            if (d == null) {
                continue;
            }
            var f = new BlockForm();
            f.id = d.id();
            try {
                f.type = d.type() == null ? BlockType.HTML : BlockType.valueOf(d.type());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Tipo de bloque desconocido: " + d.type());
            }
            f.title = d.title();
            f.html = d.html();
            f.itemId = d.itemId();
            f.sectionId = d.sectionId();
            f.limit = d.limit();
            if (d.limit() != null && (d.limit() < 1 || d.limit() > 50)) {
                throw new IllegalArgumentException("«Cuántos» va de 1 a 50");
            }
            f.links = new ArrayList<>(d.links() == null ? List.of() : d.links().stream().filter(Objects::nonNull)
                    .map(l -> LinkForm.of(l.label(), l.url())).toList());
            f.mediaId = d.mediaId();
            f.blockDepartments = checked(Attribute.DEPARTMENT, d.departments(), known);
            f.blockHotels = checked(Attribute.HOTEL, d.hotels(), known);
            f.blockJobTitles = checked(Attribute.JOB_TITLE, d.jobTitles(), known);
            out.add(f.toBlock());
        }
        return out;
    }

    static List<String> checked(Attribute attribute, List<String> values, Map<Attribute, Set<String>> known) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        for (var v : values) {
            if (!known.get(attribute).contains(v)) {
                throw new IllegalArgumentException("«" + v + "» no es un valor de " + attribute.name().toLowerCase()
                        + " del directorio");
            }
        }
        return List.copyOf(new LinkedHashSet<>(values));
    }

    static ItemContent withBlocks(ItemContent d, List<Block> blocks) {
        return new ItemContent(d.sectionId(), d.audience(), d.translations(), blocks, d.publishAt(), d.expireAt(),
                d.readTracking(), d.confirmationRequired(), d.confirmationDeadline(), d.url());
    }

    static List<String> nonNull(List<String> values) {
        return values == null ? List.of() : values;
    }

    // ── who, and what ───────────────────────────────────────────────────────────────────────────────

    /** The caller, if they hold an editorial permission — the rule of {@code @Editors}. */
    Reader editor(HttpServletRequest request) {
        var reader = readers.real(request::getHeader);
        if (reader.permissions().isEmpty()) {
            throw new SecurityException("El editor visual es para quien tiene un permiso editorial (content:edit, "
                    + "content:publish o content:admin)");
        }
        return reader;
    }

    Item page(String id) {
        var item = items.find(id).orElseThrow(() -> new NoSuchElementException("No existe el contenido " + id));
        if (item.type() != ItemType.PAGE) {
            throw new IllegalArgumentException("El editor visual es para páginas; «" + id + "» es un "
                    + item.type().label().toLowerCase());
        }
        return item;
    }

    static final class Conflict extends RuntimeException {
        Conflict(String message) {
            super(message);
        }
    }

    // ── errors, as the editor shows them ────────────────────────────────────────────────────────────

    @ExceptionHandler(SecurityException.class)
    ResponseEntity<Map<String, String>> forbidden(SecurityException e) {
        return error(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(UserFacingException.class)
    ResponseEntity<Map<String, String>> refused(UserFacingException e) {
        return error(e.getCause() instanceof SecurityException ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> invalid(RuntimeException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String, String>> missing(NoSuchElementException e) {
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(Conflict.class)
    ResponseEntity<Map<String, String>> conflict(Conflict e) {
        return error(HttpStatus.CONFLICT, e.getMessage());
    }

    static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(Map.of("error", message == null ? status.getReasonPhrase() : message));
    }
}
