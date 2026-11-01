package io.mateu.ecdemo1.contenthub.domain.item;

import io.mateu.ecdemo1.contenthub.domain.audience.Audience;

import java.util.List;

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
