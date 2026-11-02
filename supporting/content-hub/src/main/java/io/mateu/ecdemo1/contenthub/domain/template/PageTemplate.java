package io.mateu.ecdemo1.contenthub.domain.template;

import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.domain.site.Site;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Where a new page starts from: a name, what it is for, the blocks a page of its kind usually has (the
 * same {@link Block}s a page has, with placeholder texts the editor replaces) and, optionally, the
 * audience such a page is usually for. A page made from a template is an ordinary draft — copying, not
 * linking: changing the template later changes no page made from it.
 *
 * <p>A template is global (no site: offered in every site — the intranet and each newsletter) or of one
 * site ({@link #siteId}), e.g. a newsletter's page layout with its own section blocks.
 */
public class PageTemplate {

    /**
     * The language of a template's blocks' own text (the backoffice's); their other languages are their
     * translations. A page in another language gets its own language's text as the blocks' own
     * ({@link Block#rebased}).
     */
    public static final String LANGUAGE = "es";

    private String id;
    private String name;
    private String description;
    private List<Block> blocks;
    private Audience defaultAudience;
    private String siteId;
    private String updatedBy;
    private Instant updatedAt;

    @SuppressWarnings("unused") // for the store
    private PageTemplate() {
    }

    public static PageTemplate create(String id, String name, String description, List<Block> blocks,
                                      Audience defaultAudience, String by, Instant now) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Una plantilla necesita su id");
        }
        var template = new PageTemplate();
        template.id = id;
        template.edit(name, description, blocks, defaultAudience, by, now);
        return template;
    }

    /**
     * A template from a page's draft: its blocks and its audience (as the default one). The page's texts
     * stay with the page — only its structure is reused.
     */
    public static PageTemplate fromPage(String id, Item page, String name, String description, String by, Instant now) {
        if (page.type() != ItemType.PAGE) {
            throw new IllegalArgumentException("Solo una página se guarda como plantilla");
        }
        var audience = page.draft().audience() instanceof Audience.Everyone ? null : page.draft().audience();
        var template = create(id, name, description, page.draft().blocks().stream()
                .map(b -> b.rebased(page.defaultLanguage(), LANGUAGE)).toList(), audience, by, now);
        // a newsletter's page makes a template of that newsletter (its blocks may be its sections); the
        // intranet's, a global one
        template.restrictTo(Site.INTRANET.equals(page.siteId()) ? null : page.siteId());
        return template;
    }

    public void edit(String name, String description, List<Block> blocks, Audience defaultAudience, String by, Instant now) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Una plantilla necesita su nombre");
        }
        var list = blocks == null ? List.<Block>of() : List.copyOf(blocks);
        var ids = new HashSet<String>();
        for (var block : list) {
            if (!ids.add(block.id())) {
                throw new IllegalArgumentException("Dos bloques con el mismo id: " + block.id());
            }
            if (!Block.isNarrowing(block.audience())) {
                throw new IllegalArgumentException("La audiencia del bloque «" + (block.title() == null || block.title().isBlank()
                        ? block.id() : block.title()) + "» solo puede estrechar la de la página (departamentos, hoteles, puestos)");
            }
        }
        this.name = name.trim();
        this.description = description == null ? "" : description.trim();
        this.blocks = new ArrayList<>(list);
        this.defaultAudience = defaultAudience instanceof Audience.Everyone ? null : defaultAudience;
        this.updatedBy = by;
        this.updatedAt = now;
    }

    /** Of one site only (the intranet included); null: global. */
    public void restrictTo(String siteId) {
        this.siteId = siteId == null || siteId.isBlank() ? null : siteId;
    }

    /** Whether it is offered for a new page of this site. */
    public boolean availableIn(String site) {
        return siteId == null || siteId.equals(Site.of(site));
    }

    /**
     * What a new page made from this template starts with: its blocks, its default audience (everyone, if
     * none) and the title the editor gave, in the page's language. Nothing else: dates, flags and the rest
     * are the form's defaults.
     */
    public ItemContent newPageContent(String title, String language, String sectionId) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("La página nueva necesita un título");
        }
        var lang = language == null || language.isBlank() ? "es" : language;
        return new ItemContent(sectionId == null || sectionId.isBlank() ? null : sectionId,
                defaultAudience == null ? Audience.EVERYONE : defaultAudience,
                Map.of(lang, new Translation(title.trim(), "", "")),
                blocks.stream().map(b -> b.rebased(LANGUAGE, lang)).toList(), null, null, false, false,
                null, null);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public List<Block> blocks() {
        return List.copyOf(blocks);
    }

    /** The audience a page made from it starts with; null: everyone. */
    public Audience defaultAudience() {
        return defaultAudience;
    }

    /** Its site; null: global, offered in every site. */
    public String siteId() {
        return siteId;
    }

    public String updatedBy() {
        return updatedBy;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
