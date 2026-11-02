package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.item.ItemType;

import java.util.List;
import java.util.Set;

/**
 * What a consumer asks for — never whom it is for: that is the hash, and the store applies it.
 *
 * @param types     only these types (empty: all)
 * @param sectionId only this section
 * @param itemIds   only these items (favourites, a newsletter's selection, a featured block)
 * @param text      free text (the search)
 * @param siteId    the site listed (the intranet unless said otherwise); a query by {@code itemIds} crosses sites
 */
public record ContentQuery(Set<ItemType> types, String sectionId, List<String> itemIds, String text, int offset,
                           int limit, String siteId) {

    public ContentQuery {
        types = types == null ? Set.of() : Set.copyOf(types);
        limit = limit <= 0 ? 20 : limit;
        offset = Math.max(0, offset);
        siteId = io.mateu.ecdemo1.contenthub.domain.site.Site.of(siteId);
    }

    /** A query of the intranet's content. */
    public ContentQuery(Set<ItemType> types, String sectionId, List<String> itemIds, String text, int offset, int limit) {
        this(types, sectionId, itemIds, text, offset, limit, null);
    }

    /** The same, of another site's content. */
    public ContentQuery in(String site) {
        return new ContentQuery(types, sectionId, itemIds, text, offset, limit, site);
    }

    public static ContentQuery latest(int limit) {
        return new ContentQuery(Set.of(), null, null, null, 0, limit);
    }

    public static ContentQuery of(ItemType type, int offset, int limit) {
        return new ContentQuery(Set.of(type), null, null, null, offset, limit);
    }

    public static ContentQuery section(String sectionId, int offset, int limit) {
        return new ContentQuery(Set.of(), sectionId, null, null, offset, limit);
    }

    public static ContentQuery ids(List<String> ids) {
        return new ContentQuery(Set.of(), null, ids, null, 0, Math.max(1, ids.size()));
    }

    public static ContentQuery search(String text, int offset, int limit) {
        return new ContentQuery(Set.of(), null, null, text, offset, limit);
    }
}
