package io.mateu.ecdemo1.contenthub.application.health;

import io.mateu.ecdemo1.contenthub.application.menu.NewsletterTargets;
import io.mateu.ecdemo1.contenthub.domain.health.Finding.Subject;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.media.MediaReferences;
import io.mateu.ecdemo1.contenthub.domain.menu.MenuTarget;
import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterIssue;
import io.mateu.ecdemo1.contenthub.domain.site.Site;
import io.mateu.ecdemo1.contenthub.domain.uisource.RemoteUi;
import org.jsoup.Jsoup;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * What the content readers see points at, on every site — links, images, media, items, sections, portal
 * functions, newsletter issues, other UIs — each with its site and where it is, so a finding can say "in the
 * body (en)" or "in the quick links «Turnos»". And every medium anything stored uses, for the orphans.
 */
public final class ContentReferences {

    public enum Kind {
        /** An {@code <a href>}, a quick link, a slot's link, a LINK/DOCUMENT item's URL, a URL menu entry. */
        HREF,
        /** An {@code <img src>}. */
        IMG,
        /** An {@code <img>} with no text alternative (no {@code alt}, or a blank one); the value is its src. */
        IMG_NO_ALT,
        /** A {@code [[media:id]]} mark or a MEDIA block. */
        MEDIA,
        /** An item by id: a FEATURED block, a home, a PAGE menu entry. */
        ITEM,
        /** A section by id, of the subject's site: an item's own, a listing block's, a SECTION menu entry. */
        SECTION,
        /** One of the portal's functions (a BUILTIN menu entry). */
        BUILTIN,
        /** A UI source of the registry: a REMOTE block, a remote page, a REMOTE menu entry. */
        REMOTE,
        /** A newsletter issue, by id (an ISSUE menu entry): it must have been sent. */
        ISSUE
    }

    /** One reference, from a subject of a site. {@code value} is the URL or the id it names. */
    public record Reference(String siteId, Subject subject, String subjectId, String subjectTitle, Kind kind,
                            String value, String where) {
    }

    /** A medium's URL as this service signs it, pasted into a body: it stops working in two hours. */
    static final Pattern SIGNED_MEDIA = Pattern.compile("/_intranet/media/([A-Za-z0-9_-]+)");

    private ContentReferences() {
    }

    /**
     * Everything live content points at, on every site: published items that have not expired, slots, homes,
     * menus and the newsletter issues still to be sent (a sent one cannot change any more).
     */
    public static List<Reference> live(ContentSnapshot s) {
        var out = new ArrayList<Reference>();
        for (var item : s.items()) {
            var content = s.live(item).orElse(null);
            if (content != null) {
                var site = item.siteId();
                item(r -> out.add(new Reference(site, r.subject(), r.subjectId(), r.subjectTitle(), r.kind(), r.value(),
                        r.where())), item.id(), ContentSnapshot.title(item), item.type(), content);
            }
        }
        for (var slot : s.slots()) {
            Consumer<Reference> add = out::add;
            var title = slot.description() == null || slot.description().isBlank() ? slot.key() : slot.description();
            for (var v : slot.variants()) {
                html(add, null, Subject.SLOT, slot.key(), title, v.html(), "variante «" + v.id() + "»");
                if (v.linkUrl() != null && !v.linkUrl().isBlank()) {
                    add.accept(new Reference(null, Subject.SLOT, slot.key(), title, Kind.HREF, v.linkUrl().trim(),
                            "enlace de la variante «" + v.id() + "»"));
                }
            }
            html(add, null, Subject.SLOT, slot.key(), title, slot.fallback(), "contenido por defecto");
        }
        s.homes().forEach((site, ids) -> {
            for (int i = 0; i < ids.size(); i++) {
                out.add(new Reference(site, Subject.HOMES, "homes:" + site, "Inicios de «" + s.siteName(site) + "»",
                        Kind.ITEM, ids.get(i), "inicio nº " + (i + 1)));
            }
        });
        for (var menu : s.menus()) {
            for (var entry : menu.entries()) {
                if (!entry.active() || entry.target() == null) {
                    continue;
                }
                var t = entry.target();
                var kind = switch (t.kind()) {
                    case MenuTarget.PAGE -> Kind.ITEM;
                    case MenuTarget.SECTION -> Kind.SECTION;
                    case MenuTarget.BUILTIN -> Kind.BUILTIN;
                    case MenuTarget.URL -> Kind.HREF;
                    case MenuTarget.REMOTE -> Kind.REMOTE;
                    case NewsletterTargets.ISSUE -> Kind.ISSUE;
                    default -> null; // its home or archive, or an extension's kind: nothing to look up
                };
                if (kind != null) {
                    out.add(new Reference(menu.siteId(), Subject.MENU, menu.siteId() + "/" + entry.id(),
                            "Menú de «" + s.siteName(menu.siteId()) + "»: "
                                    + entry.label(menu.defaultLanguage(), menu.defaultLanguage()),
                            kind, kind == Kind.REMOTE ? t.remoteSource() : t.value(), "destino de la entrada"));
                }
            }
        }
        for (var issue : s.issues()) {
            if (issue.status() != NewsletterIssue.Status.DRAFT && issue.status() != NewsletterIssue.Status.SCHEDULED) {
                continue;
            }
            var site = issue.newsletterId();
            var title = "Número " + issue.number() + " de «" + s.siteName(site) + "»"
                    + (issue.subject() == null || issue.subject().isBlank() ? "" : ": " + issue.subject());
            for (var b : issue.blocks()) {
                block(r -> out.add(new Reference(site, Subject.ISSUE, issue.id(), title, r.kind(), r.value(), r.where())),
                        issue.id(), title, b, where(b));
            }
        }
        return out;
    }

    /** An item's references, before the site is put on them. */
    static void item(Consumer<Reference> add, String id, String title, ItemType type, ItemContent c) {
        c.translations().forEach((language, t) -> html(add, null, Subject.ITEM, id, title, t.body(), "cuerpo (" + language + ")"));
        if (type == ItemType.REMOTE_PAGE) {
            RemoteUi.parse(c.url()).ifPresentOrElse(
                    r -> add.accept(new Reference(null, Subject.ITEM, id, title, Kind.REMOTE, r.sourceId(), "pantalla externa")),
                    () -> add.accept(new Reference(null, Subject.ITEM, id, title, Kind.REMOTE, String.valueOf(c.url()),
                            "pantalla externa")));
        } else if ((type == ItemType.LINK || type == ItemType.DOCUMENT) && c.url() != null && !c.url().isBlank()) {
            add.accept(RemoteUi.isRemote(c.url())
                    ? new Reference(null, Subject.ITEM, id, title, Kind.REMOTE,
                    RemoteUi.parse(c.url()).map(RemoteUi::sourceId).orElse(c.url()), "URL del contenido")
                    : new Reference(null, Subject.ITEM, id, title, Kind.HREF, c.url().trim(), "URL del contenido"));
        }
        if (c.sectionId() != null && !c.sectionId().isBlank()) {
            add.accept(new Reference(null, Subject.ITEM, id, title, Kind.SECTION, c.sectionId(), "su sección"));
        }
        for (var b : c.blocks()) {
            block(add, id, title, b, where(b));
        }
    }

    static String where(Block b) {
        return "bloque " + b.type().label().toLowerCase(Locale.ROOT)
                + (b.title() == null || b.title().isBlank() ? "" : " «" + b.title() + "»");
    }

    static void block(Consumer<Reference> add, String id, String title, Block b, String where) {
        if (b.type() == BlockType.MEDIA && b.mediaId() != null && !b.mediaId().isBlank()) {
            add.accept(new Reference(null, Subject.ITEM, id, title, Kind.MEDIA, b.mediaId(), where));
        }
        if (b.type() == BlockType.FEATURED && b.itemId() != null && !b.itemId().isBlank()) {
            add.accept(new Reference(null, Subject.ITEM, id, title, Kind.ITEM, b.itemId(), where));
        }
        if (b.type() == BlockType.REMOTE && b.remote() != null) {
            add.accept(new Reference(null, Subject.ITEM, id, title, Kind.REMOTE, b.remote().sourceId(), where));
        }
        if (b.sectionId() != null && !b.sectionId().isBlank()) {
            add.accept(new Reference(null, Subject.ITEM, id, title, Kind.SECTION, b.sectionId(), where));
        }
        for (var link : b.links()) {
            if (link.url() != null && !link.url().isBlank()) {
                var kind = RemoteUi.isRemote(link.url()) ? Kind.REMOTE : Kind.HREF;
                add.accept(new Reference(null, Subject.ITEM, id, title, kind,
                        kind == Kind.REMOTE ? RemoteUi.parse(link.url()).map(RemoteUi::sourceId).orElse(link.url()) : link.url().trim(),
                        where + (link.label() == null || link.label().isBlank() ? "" : ", «" + link.label() + "»")));
            }
        }
        html(add, null, Subject.ITEM, id, title, b.html(), where);
    }

    /** The links, images and media marks of a piece of HTML. */
    static void html(Consumer<Reference> add, String site, Subject subject, String id, String title, String html,
                     String where) {
        if (html == null || html.isBlank()) {
            return;
        }
        var marks = MediaReferences.MARK.matcher(html);
        while (marks.find()) {
            add.accept(new Reference(site, subject, id, title, Kind.MEDIA, marks.group(1), where));
        }
        var doc = Jsoup.parseBodyFragment(html);
        for (var a : doc.select("a[href]")) {
            add.accept(new Reference(site, subject, id, title, Kind.HREF, a.attr("href").trim(), where));
        }
        for (var img : doc.select("img[src]")) {
            add.accept(new Reference(site, subject, id, title, Kind.IMG, img.attr("src").trim(), where));
            if (img.attr("alt").isBlank()) {
                add.accept(new Reference(site, subject, id, title, Kind.IMG_NO_ALT, img.attr("src").trim(), where));
            }
        }
    }

    /**
     * Every medium anything stored uses — drafts, every version, slots, every newsletter issue and layout — so
     * nothing an old version, a sent issue or an unpublished draft still shows is called an orphan.
     */
    public static Set<String> mediaInUse(ContentSnapshot s) {
        var out = new LinkedHashSet<String>();
        for (var item : s.items()) {
            out.addAll(MediaReferences.in(item.draft()));
            signed(item.draft(), out);
            for (var v : item.versions()) {
                out.addAll(MediaReferences.in(v.content()));
                signed(v.content(), out);
            }
        }
        for (var slot : s.slots()) {
            marks(slot.fallback(), out);
            slot.variants().forEach(v -> marks(v.html(), out));
        }
        s.issues().forEach(i -> blocks(i.blocks(), out));
        s.layouts().values().forEach(l -> blocks(l, out));
        return out;
    }

    static void blocks(List<Block> blocks, Set<String> out) {
        for (var b : blocks) {
            if (b.type() == BlockType.MEDIA && b.mediaId() != null) {
                out.add(b.mediaId());
            }
            marks(b.html(), out);
        }
    }

    static void signed(ItemContent c, Set<String> out) {
        c.translations().values().forEach(t -> marks(t.body(), out));
        c.blocks().forEach(b -> marks(b.html(), out));
    }

    static void marks(String html, Set<String> out) {
        if (html == null) {
            return;
        }
        var m = MediaReferences.MARK.matcher(html);
        while (m.find()) {
            out.add(m.group(1));
        }
        var signed = SIGNED_MEDIA.matcher(html);
        while (signed.find()) {
            out.add(signed.group(1));
        }
    }

    // ---- what a URL is ------------------------------------------------------------------------------------

    /** What a URL found in content is. */
    public sealed interface Target {
    }

    /** Not ours to check: an anchor, mail, phone, or a scheme that is not the web. */
    public record Ignored() implements Target {
    }

    public record Malformed(String reason) implements Target {
    }

    /** A path of this host (/intranet/…), whatever host or UI prefix it came with. */
    public record Internal(String path) implements Target {
    }

    public record External(String url) implements Target {
    }

    /** A signed media URL pasted as is: the medium's id. */
    public record SignedMedia(String mediaId) implements Target {
    }

    public static Target classify(String raw, Set<String> internalHosts) {
        if (raw == null || raw.isBlank()) {
            return new Malformed("enlace vacío");
        }
        var url = raw.trim();
        var lower = url.toLowerCase(Locale.ROOT);
        if (url.startsWith("#") || lower.startsWith("mailto:") || lower.startsWith("tel:")) {
            return new Ignored();
        }
        var signed = SIGNED_MEDIA.matcher(url);
        if (signed.find() && (url.startsWith("/") || isOurs(url, internalHosts))) {
            return new SignedMedia(signed.group(1));
        }
        URI uri;
        try {
            uri = URI.create(url.replace(" ", "%20"));
        } catch (IllegalArgumentException e) {
            return new Malformed("URL mal formada");
        }
        var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.isEmpty()) {
            if (url.startsWith("//")) {
                return classify("https:" + url, internalHosts);
            }
            return url.startsWith("/") ? new Internal(normalise(uri.getPath())) : new Ignored();
        }
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return new Ignored();
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return new Malformed("URL sin dominio");
        }
        if (internalHosts.contains(uri.getHost().toLowerCase(Locale.ROOT))) {
            return new Internal(normalise(uri.getPath()));
        }
        return new External(url);
    }

    static boolean isOurs(String url, Set<String> internalHosts) {
        try {
            var host = URI.create(url.replace(" ", "%20")).getHost();
            return host != null && internalHosts.contains(host.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Without the UI's prefix (/_intranet, /_portal) or a trailing slash: the portal's route. */
    static String normalise(String path) {
        var p = path == null || path.isBlank() ? "/" : path;
        for (var prefix : List.of("/_intranet", "/_portal")) {
            if (p.equals(prefix) || p.startsWith(prefix + "/")) {
                p = p.substring(prefix.length());
            }
        }
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p.isEmpty() ? "/" : p;
    }
}
