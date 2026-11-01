package io.mateu.ecdemo1.contenthub.application.usecases;

import io.mateu.ecdemo1.contenthub.application.out.HomePages;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.NewsletterRepository;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemStatus;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterEdition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The weekly newsletter, composed by itself: one edition per ISO week ({@code w-2026-W45}) with what
 * became visible since the previous weekly edition (or in the last seven days) — news, notices and
 * pages, newest first, not the pages used as homes. For everyone: each group of recipients still gets
 * only the items it can see. It is left as a DRAFT for an editor to review and schedule, unless the
 * caller asks to send it as it is.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WeeklyNewsletter {

    static final Set<ItemType> TYPES = Set.of(ItemType.ARTICLE, ItemType.NOTICE, ItemType.PAGE);
    static final int MAX_ITEMS = 12;

    final NewsletterRepository editions;
    final ItemRepository items;
    final HomePages homes;

    /** The id of this week's edition (Europe/Madrid weeks). */
    public static String weekId(Instant now) {
        var day = now.atZone(ZoneId.of("Europe/Madrid")).toLocalDate();
        return String.format("w-%d-W%02d", day.get(IsoFields.WEEK_BASED_YEAR), day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /**
     * Composes this week's edition if it does not exist yet and there is something to tell.
     *
     * @param send schedule it right away (sent within a minute) instead of leaving it as a draft
     * @return the new edition's id; empty if it already existed or nothing was published
     */
    @Transactional
    public Optional<String> compose(Instant now, boolean send) {
        var id = weekId(now);
        if (editions.find(id).isPresent()) {
            return Optional.empty();
        }
        var since = editions.findAll().stream().filter(e -> e.id().startsWith("w-") && e.scheduledAt() != null)
                .map(NewsletterEdition::scheduledAt).max(Comparator.naturalOrder())
                .orElse(now.minus(Duration.ofDays(7)));
        var homePages = new HashSet<>(homes.pageIds());
        List<String> ids = items.findAll().stream()
                .filter(i -> i.status() == ItemStatus.PUBLISHED && TYPES.contains(i.type()) && !homePages.contains(i.id()))
                .filter(i -> visibleSince(i, since, now))
                .sorted(Comparator.comparing((Item i) -> publishedAt(i)).reversed())
                .limit(MAX_ITEMS).map(Item::id).toList();
        if (ids.isEmpty()) {
            log.info("Weekly newsletter {}: nothing published since {}", id, since);
            return Optional.empty();
        }
        var day = now.atZone(ZoneId.of("Europe/Madrid")).toLocalDate();
        var edition = NewsletterEdition.compose(id, "Boletín de la semana " + day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                "<p>Lo que se ha publicado en la intranet esta semana.</p>", ids, Audience.EVERYONE);
        if (send) {
            edition.schedule(now);
        }
        editions.save(edition);
        log.info("Weekly newsletter {} composed with {} item(s){}", id, ids.size(), send ? ", scheduled" : " (draft)");
        return Optional.of(id);
    }

    static Instant publishedAt(Item i) {
        return i.publishedVersion().map(v -> v.content().publishAt() != null ? v.content().publishAt() : v.publishedAt())
                .orElse(Instant.EPOCH);
    }

    /** Became visible in (since, now] and has not expired. */
    static boolean visibleSince(Item i, Instant since, Instant now) {
        var v = i.publishedVersion().orElse(null);
        if (v == null) {
            return false;
        }
        var at = publishedAt(i);
        var expire = v.content().expireAt();
        return at.isAfter(since) && !at.isAfter(now) && (expire == null || expire.isAfter(now));
    }
}
