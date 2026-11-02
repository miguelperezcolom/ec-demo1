package io.mateu.ecdemo1.contenthub.domain.item;

import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.uisource.RemoteUi;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

/**
 * A component inside a page — or, without a page, a slot's variant. Its audience can only narrow its
 * page's: the effective one is always {@code page AND block} ({@link Audience#effective}).
 *
 * <p>Its human text — {@code title}, {@code html} and the links' labels — is in the DEFAULT LANGUAGE of
 * whatever holds it (the page's, the newsletter's): that is what every reader gets unless
 * {@code translations} has their language. A block written before blocks had languages is exactly that,
 * with no translations: there is nothing to migrate. URLs, items, media and audience are the same in every
 * language.
 *
 * @param itemId       FEATURED: the item shown
 * @param sectionId    AUTOMATIC_LISTING: the section listed (null: all)
 * @param limit        AUTOMATIC_LISTING: how many
 * @param links        QUICK_LINKS
 * @param mediaId      MEDIA: the media asset shown
 * @param remote       REMOTE: the registered UI source shown, and its route or attributes
 * @param translations the text in the other languages ({@link BlockText}); what a language lacks, its
 *                     reader gets in the default language
 */
public record Block(String id, BlockType type, Audience audience, String title, String html, String itemId,
                    String sectionId, Integer limit, List<Link> links, String mediaId, RemoteUi remote,
                    Map<String, BlockText> translations) {

    /** A block with its text in the default language only. */
    public Block(String id, BlockType type, Audience audience, String title, String html, String itemId,
                 String sectionId, Integer limit, List<Link> links, String mediaId, RemoteUi remote) {
        this(id, type, audience, title, html, itemId, sectionId, limit, links, mediaId, remote, null);
    }

    /** A block that shows no external UI — every type but REMOTE. */
    public Block(String id, BlockType type, Audience audience, String title, String html, String itemId,
                 String sectionId, Integer limit, List<Link> links, String mediaId) {
        this(id, type, audience, title, html, itemId, sectionId, limit, links, mediaId, null, null);
    }

    public record Link(String label, String url) {
    }

    /**
     * A block's text in one language other than the default. Each part may be missing (null, or a link with no
     * label here): its reader gets that part in the default language.
     *
     * @param labels the quick links' labels, by URL — the URLs are the block's, the same in every language
     */
    public record BlockText(String title, String html, Map<String, String> labels) {
        public BlockText {
            title = title == null || title.isBlank() ? null : title.trim();
            html = html == null || html.isBlank() ? null : html;
            var copy = new TreeMap<String, String>();
            if (labels != null) {
                labels.forEach((url, label) -> {
                    if (url != null && label != null && !label.isBlank()) {
                        copy.put(url, label.trim());
                    }
                });
            }
            labels = Collections.unmodifiableMap(copy);
        }

        public static final BlockText NONE = new BlockText(null, null, null);

        public boolean isEmpty() {
            return title == null && html == null && labels.isEmpty();
        }
    }

    /**
     * The attributes a block narrows its page's audience by — what the editors offer ("solo estos
     * departamentos, hoteles, puestos"). Whatever a block says is ANDed with its page anyway; keeping
     * its own audience to these leaves is what lets the editor show it as a plain narrowing.
     */
    public static final Set<Attribute> NARROWING = EnumSet.of(Attribute.DEPARTMENT, Attribute.HOTEL, Attribute.JOB_TITLE);

    /**
     * Whether an audience is a block's narrowing as the editors write it: everyone (the page's audience
     * as it is), or a conjunction of {@code attribute IN (values)} over {@link #NARROWING}, each with
     * at least one value. No OR, no named audience: nothing that reads as "also these".
     */
    public static boolean isNarrowing(Audience audience) {
        return switch (audience) {
            case null -> true;
            case Audience.Everyone e -> true;
            case Audience.AttributeIn in -> NARROWING.contains(in.attribute()) && !in.values().isEmpty();
            case Audience.AllOf all -> all.parts().stream().allMatch(p -> p instanceof Audience.AttributeIn && isNarrowing(p));
            default -> false;
        };
    }

    public Block {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A block needs its id");
        }
        if (type == null) {
            throw new IllegalArgumentException("A block needs its type");
        }
        audience = audience == null ? Audience.EVERYONE : audience;
        links = links == null ? List.of() : List.copyOf(links);
        if (type == BlockType.REMOTE && remote == null) {
            throw new IllegalArgumentException("Un componente remoto necesita su UI externa");
        }
        if (type != BlockType.REMOTE) {
            remote = null;
        }
        var texts = new TreeMap<String, BlockText>();
        if (translations != null) {
            translations.forEach((language, text) -> {
                if (language != null && !language.isBlank() && text != null && !text.isEmpty()) {
                    texts.put(language, text);
                }
            });
        }
        translations = Collections.unmodifiableMap(texts);
    }

    /**
     * The block's own audience as the visibility function projects it: what the editor chose, ANDed — for
     * a remote component — with who may use its source ({@link RemoteUi#audienceReference}). The editor
     * still sees and edits only {@link #audience}; the page's is ANDed on top ({@link Audience#effective}).
     */
    public Audience visibilityAudience() {
        return remote == null ? audience : Audience.effective(audience, remote.audienceReference());
    }

    /** The same block with another text (the sanitiser's), everything else kept. */
    public Block withHtml(String html) {
        return new Block(id, type, audience, title, html, itemId, sectionId, limit, links, mediaId, remote, translations);
    }

    /** Every HTML text of the block — the default language's and each translation's — through {@code f} (the sanitiser). */
    public Block mapHtml(UnaryOperator<String> f) {
        var texts = new LinkedHashMap<String, BlockText>();
        translations.forEach((l, t) -> texts.put(l, new BlockText(t.title(), t.html() == null ? null : f.apply(t.html()),
                t.labels())));
        return new Block(id, type, audience, title, html == null ? null : f.apply(html), itemId, sectionId, limit, links,
                mediaId, remote, texts);
    }

    /** The same block under another id (an issue's copy of its newsletter's layout), its text in every language kept. */
    public Block withId(String id) {
        return new Block(id, type, audience, title, html, itemId, sectionId, limit, links, mediaId, remote, translations);
    }

    /** The same block with other translations. */
    public Block withTranslations(Map<String, BlockText> translations) {
        return new Block(id, type, audience, title, html, itemId, sectionId, limit, links, mediaId, remote, translations);
    }

    // ── languages ───────────────────────────────────────────────────────────────────────────────────

    /**
     * The block as a reader of {@code language} sees it: each part of its text in their language where it has
     * it, in the default language where not — an item's rule (their language, else the default), part by part.
     * In the default language, or in one it has nothing in, the block as it is.
     */
    public Block in(String language, String defaultLanguage) {
        var t = language == null || language.equals(defaultLanguage) ? null : translations.get(language);
        if (t == null) {
            return this;
        }
        var shown = links.stream().map(l -> new Link(t.labels().getOrDefault(l.url(), l.label()), l.url())).toList();
        return new Block(id, type, audience, t.title() != null ? t.title() : title, t.html() != null ? t.html() : html,
                itemId, sectionId, limit, shown, mediaId, remote, translations);
    }

    /**
     * What the block says in {@code language} by itself, with no fallback: the default language's own text for
     * the default language; its translation — maybe partial, maybe none — for another.
     */
    public BlockText textIn(String language, String defaultLanguage) {
        if (language == null || language.equals(defaultLanguage)) {
            var labels = new LinkedHashMap<String, String>();
            links.forEach(l -> labels.putIfAbsent(l.url(), l.label()));
            return new BlockText(title, html, labels);
        }
        return translations.getOrDefault(language, BlockText.NONE);
    }

    /**
     * Whether a reader of {@code language} would get some of this block's text in the default language: it has
     * text there (a title, HTML, a link's label) that the language has not.
     */
    public boolean untranslatedIn(String language, String defaultLanguage) {
        if (language == null || language.equals(defaultLanguage)) {
            return false;
        }
        var t = translations.getOrDefault(language, BlockText.NONE);
        return t.title() == null && !blank(title) || t.html() == null && !blank(html)
                || links.stream().anyMatch(l -> !blank(l.label()) && !l.label().equals(l.url())
                && !t.labels().containsKey(l.url())); // a link that says its URL says it in every language
    }

    /** Whether it has human text of its own (in the default language): a title, HTML, a link's label. */
    public boolean hasText() {
        return !blank(title) || !blank(html) || links.stream().anyMatch(l -> !blank(l.label()));
    }

    /**
     * The same block with its text in {@code language} replaced: the default language's own fields (the links'
     * labels by URL — the links themselves stay), or that language's translation.
     */
    public Block withText(String language, String defaultLanguage, BlockText text) {
        var t = text == null ? BlockText.NONE : text;
        if (language == null || language.equals(defaultLanguage)) {
            var labelled = links.stream().map(l -> new Link(t.labels().getOrDefault(l.url(), l.label()), l.url())).toList();
            return new Block(id, type, audience, t.title(), t.html(), itemId, sectionId, limit, labelled, mediaId, remote,
                    translations);
        }
        var texts = new LinkedHashMap<>(translations);
        texts.put(language, t);
        return withTranslations(texts);
    }

    /**
     * The same block in a holder whose default language is {@code to} instead of {@code from} (a page made from a
     * template, a template made from a page): its own text becomes {@code to}'s — that language's where it has
     * it, the old default's where not — and the old default's text a translation, so nothing is lost.
     */
    public Block rebased(String from, String to) {
        if (from == null || to == null || from.equals(to)) {
            return this;
        }
        var old = textIn(from, from);
        var shown = in(to, from);
        var texts = new LinkedHashMap<>(translations);
        texts.remove(to);
        texts.put(from, old);
        return new Block(id, type, audience, shown.title(), shown.html(), itemId, sectionId, limit, shown.links(), mediaId,
                remote, texts);
    }

    /**
     * A block as an editor working in {@code language} left it — {@code edited} carries everything, its text being
     * that language's — over what was stored ({@code stored}; null for a new block). The structure (type, links,
     * audience…) is the edited one; the text in the other languages, the stored one. A part the editor was shown
     * as the fallback (the default language's, "sin traducir") and left as it was stays untranslated. A new block
     * written in another language takes that text as its default language's too, not to be empty there.
     */
    public static Block edited(Block stored, Block edited, String language, String defaultLanguage) {
        return edited(stored, edited, language, defaultLanguage, Objects::equals);
    }

    /**
     * As {@link #edited(Block, Block, String, String)}, telling whether two HTML texts are the same with
     * {@code sameHtml} — an editor in a browser gives back the HTML it was shown written its own way.
     */
    public static Block edited(Block stored, Block edited, String language, String defaultLanguage,
                               java.util.function.BiPredicate<String, String> sameHtml) {
        if (language == null || language.equals(defaultLanguage)) {
            return edited.withTranslations(stored == null ? Map.of() : stored.translations());
        }
        if (stored == null) {
            var text = edited.textIn(defaultLanguage, defaultLanguage);
            return edited.withTranslations(Map.of()).withText(language, defaultLanguage, text);
        }
        var own = edited.textIn(defaultLanguage, defaultLanguage); // the edited text, in `language`
        // the default language's text as stored, over the edited structure (a new link: its label as edited)
        var defaultLabels = new LinkedHashMap<String, String>();
        stored.links().forEach(l -> defaultLabels.putIfAbsent(l.url(), l.label()));
        var links = edited.links().stream().map(l -> new Link(defaultLabels.getOrDefault(l.url(), l.label()), l.url()))
                .toList();
        var base = new Block(edited.id(), edited.type(), edited.audience(), stored.title(), stored.html(), edited.itemId(),
                edited.sectionId(), edited.limit(), links, edited.mediaId(), edited.remote(), stored.translations());
        var before = stored.translations().getOrDefault(language, BlockText.NONE);
        var shown = stored.in(language, defaultLanguage); // what the editor was shown
        var title = before.title() == null && Objects.equals(own.title(), trimmed(shown.title())) ? null : own.title();
        var shownHtml = blank(shown.html()) ? null : shown.html();
        var html = before.html() == null && (own.html() == null ? shownHtml == null
                : shownHtml != null && sameHtml.test(own.html(), shownHtml)) ? null : own.html();
        var shownLabels = new LinkedHashMap<String, String>();
        shown.links().forEach(l -> shownLabels.putIfAbsent(l.url(), l.label()));
        var labels = new LinkedHashMap<>(own.labels());
        labels.entrySet().removeIf(e -> !before.labels().containsKey(e.getKey())
                && e.getValue().equals(trimmed(shownLabels.get(e.getKey()))));
        return base.withText(language, defaultLanguage, new BlockText(title, html, labels));
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    static String trimmed(String s) {
        return blank(s) ? null : s.trim();
    }
}
