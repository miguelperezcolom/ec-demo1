package io.mateu.ecdemo1.contenthub.application.health;

import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemStatus;
import io.mateu.ecdemo1.contenthub.domain.media.MediaAsset;
import io.mateu.ecdemo1.contenthub.domain.menu.SiteMenu;
import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterIssue;
import io.mateu.ecdemo1.contenthub.domain.site.Site;
import io.mateu.ecdemo1.contenthub.domain.slot.Slot;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Everything the checks look at, across every site, read once per run: the checks themselves are pure
 * functions of it.
 *
 * @param altTexts        the library's text alternatives, by medium
 * @param sites           every site (the intranet, each newsletter): id → name
 * @param homes           each site's homes, in order: site → page ids
 * @param sections        each site's sections: site → section ids
 * @param menus           every site's configurable menu
 * @param issues          the newsletters' issues
 * @param layouts         each newsletter's layout blocks (what every new issue starts from): site → blocks
 * @param templates       the page templates (what new pages start from)
 * @param uiSources       the registry of UI sources: id → whether it is active
 * @param personIds       the employees (a /intranet/personas/{id} link)
 * @param internalHosts   the hosts whose URLs are the portal's own (the portal URL's)
 * @param viewedItemIds   the items someone opened since the "nobody opens it" window's start
 * @param analyticsSince  since when usage is recorded; null: no analytics, so no "nobody opens it"
 */
public record ContentSnapshot(Instant now, List<Item> items, List<MediaAsset> media, Map<String, String> altTexts,
                              List<Slot> slots, Map<String, String> sites, Map<String, List<String>> homes,
                              Map<String, Set<String>> sections, List<SiteMenu> menus, List<NewsletterIssue> issues,
                              Map<String, List<Block>> layouts,
                              List<io.mateu.ecdemo1.contenthub.domain.template.PageTemplate> templates,
                              Map<String, Boolean> uiSources, Set<String> personIds,
                              Set<String> surveyIds, Set<String> internalHosts, Set<String> viewedItemIds,
                              Instant analyticsSince) {

    public ContentSnapshot {
        items = items == null ? List.of() : List.copyOf(items);
        media = media == null ? List.of() : List.copyOf(media);
        altTexts = altTexts == null ? Map.of() : Map.copyOf(altTexts);
        slots = slots == null ? List.of() : List.copyOf(slots);
        sites = sites == null ? Map.of(Site.INTRANET, "Intranet") : Map.copyOf(sites);
        homes = homes == null ? Map.of() : Map.copyOf(homes);
        sections = sections == null ? Map.of() : Map.copyOf(sections);
        menus = menus == null ? List.of() : List.copyOf(menus);
        issues = issues == null ? List.of() : List.copyOf(issues);
        layouts = layouts == null ? Map.of() : Map.copyOf(layouts);
        templates = templates == null ? List.of() : List.copyOf(templates);
        uiSources = uiSources == null ? Map.of() : Map.copyOf(uiSources);
        personIds = personIds == null ? Set.of() : Set.copyOf(personIds);
        surveyIds = surveyIds == null ? Set.of() : Set.copyOf(surveyIds);
        internalHosts = internalHosts == null ? Set.of() : Set.copyOf(internalHosts);
        viewedItemIds = viewedItemIds == null ? Set.of() : Set.copyOf(viewedItemIds);
    }

    /** Where an item stands for whoever links to it. */
    public enum ItemState { OK, MISSING, UNPUBLISHED, RETIRED, EXPIRED }

    public Optional<Item> item(String id) {
        return items.stream().filter(i -> i.id().equals(id)).findFirst();
    }

    public boolean section(String siteId, String sectionId) {
        return sections.getOrDefault(Site.of(siteId), Set.of()).contains(sectionId);
    }

    public Optional<NewsletterIssue> issue(String id) {
        return issues.stream().filter(i -> i.id().equals(id)).findFirst();
    }

    public String siteName(String siteId) {
        return sites.getOrDefault(Site.of(siteId), siteId);
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
