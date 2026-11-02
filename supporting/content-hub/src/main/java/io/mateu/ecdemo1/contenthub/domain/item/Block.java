package io.mateu.ecdemo1.contenthub.domain.item;

import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;

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
 */
public record Block(String id, BlockType type, Audience audience, String title, String html, String itemId,
                    String sectionId, Integer limit, List<Link> links, String mediaId) {

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
    }
}
