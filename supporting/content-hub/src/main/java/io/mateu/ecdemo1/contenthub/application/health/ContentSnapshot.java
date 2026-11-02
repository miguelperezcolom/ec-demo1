package io.mateu.ecdemo1.contenthub.application.health;

import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemStatus;
import io.mateu.ecdemo1.contenthub.domain.media.MediaAsset;
import io.mateu.ecdemo1.contenthub.domain.menu.SiteMenu;
import io.mateu.ecdemo1.contenthub.domain.slot.Slot;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Everything the checks look at, read once per run: the checks themselves are pure functions of it.
 *
 * @param altTexts        the library's text alternatives, by medium
 * @param homePageIds     the pages that are homes, in order
 * @param menus           the sites' configurable menus
 * @param uiSourceIds     the UI sources a REMOTE menu target may name
 * @param personIds       the employees (a /intranet/personas/{id} link)
 * @param otherHtml       other stored HTML that may embed media (newsletter intros)
 * @param internalHosts   the hosts whose URLs are the portal's own (the portal URL's)
 * @param viewedItemIds   the items someone opened since {@code analyticsSince} (or the window's start)
 * @param analyticsSince  since when usage is recorded; null: no analytics, so no "nobody opens it"
 */
public record ContentSnapshot(Instant now, List<Item> items, List<MediaAsset> media, Map<String, String> altTexts,
                              List<Slot> slots, List<String> homePageIds, Set<String> sectionIds,
                              List<SiteMenu> menus, Set<String> uiSourceIds, Set<String> personIds,
                              Set<String> surveyIds, List<String> otherHtml, Set<String> internalHosts,
                              Set<String> viewedItemIds, Instant analyticsSince) {

    public ContentSnapshot {
        items = items == null ? List.of() : List.copyOf(items);
        media = media == null ? List.of() : List.copyOf(media);
        altTexts = altTexts == null ? Map.of() : Map.copyOf(altTexts);
        slots = slots == null ? List.of() : List.copyOf(slots);
        homePageIds = homePageIds == null ? List.of() : List.copyOf(homePageIds);
        sectionIds = sectionIds == null ? Set.of() : Set.copyOf(sectionIds);
        menus = menus == null ? List.of() : List.copyOf(menus);
        uiSourceIds = uiSourceIds == null ? Set.of() : Set.copyOf(uiSourceIds);
        personIds = personIds == null ? Set.of() : Set.copyOf(personIds);
        surveyIds = surveyIds == null ? Set.of() : Set.copyOf(surveyIds);
        otherHtml = otherHtml == null ? List.of() : List.copyOf(otherHtml);
        internalHosts = internalHosts == null ? Set.of() : Set.copyOf(internalHosts);
        viewedItemIds = viewedItemIds == null ? Set.of() : Set.copyOf(viewedItemIds);
    }

    /** Where an item stands for whoever links to it. */
    public enum ItemState { OK, MISSING, UNPUBLISHED, RETIRED, EXPIRED }

    Map<String, Item> itemsById() {
        return items.stream().collect(Collectors.toMap(Item::id, Function.identity(), (a, b) -> a));
    }

    public Optional<Item> item(String id) {
        return items.stream().filter(i -> i.id().equals(id)).findFirst();
    }

    public ItemState itemState(String id) {
        var item = item(id).orElse(null);
        if (item == null) {
            return ItemState.MISSING;
        }
        if (item.status() == ItemStatus.RETIRED) {
            return ItemState.RETIRED;
        }
        var published = item.publishedVersion().orElse(null);
        if (published == null) {
            return ItemState.UNPUBLISHED;
        }
        var expireAt = published.content().expireAt();
        return expireAt != null && !expireAt.isAfter(now) ? ItemState.EXPIRED : ItemState.OK;
    }

    /** What readers see of a published item that has not expired (scheduled ones included: they soon will). */
    public Optional<ItemContent> live(Item item) {
        return item.publishedVersion().map(v -> v.content())
                .filter(c -> c.expireAt() == null || c.expireAt().isAfter(now));
    }

    public static String title(Item item) {
        var content = item.publishedVersion().map(v -> v.content()).orElse(item.draft());
        var title = content.in(item.defaultLanguage(), item.defaultLanguage()).title();
        return title == null || title.isBlank() ? item.id() : title;
    }
}
