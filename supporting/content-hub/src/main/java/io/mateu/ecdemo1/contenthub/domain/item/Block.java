package io.mateu.ecdemo1.contenthub.domain.item;

import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.uisource.RemoteUi;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A component inside a page — or, without a page, a slot's variant. Its audience can only narrow its
 * page's: the effective one is always {@code page AND block} ({@link Audience#effective}).
 *
 * @param itemId    FEATURED: the item shown
 * @param sectionId AUTOMATIC_LISTING: the section listed (null: all)
 * @param limit     AUTOMATIC_LISTING: how many
 * @param links     QUICK_LINKS
 * @param mediaId   MEDIA: the media asset shown
 * @param remote    REMOTE: the registered UI source shown, and its route or attributes
 */
public record Block(String id, BlockType type, Audience audience, String title, String html, String itemId,
                    String sectionId, Integer limit, List<Link> links, String mediaId, RemoteUi remote) {

    /** A block that shows no external UI — every type but REMOTE. */
    public Block(String id, BlockType type, Audience audience, String title, String html, String itemId,
                 String sectionId, Integer limit, List<Link> links, String mediaId) {
        this(id, type, audience, title, html, itemId, sectionId, limit, links, mediaId, null);
    }

    public record Link(String label, String url) {
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
        return new Block(id, type, audience, title, html, itemId, sectionId, limit, links, mediaId, remote);
    }
}
