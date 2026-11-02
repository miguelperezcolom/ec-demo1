package io.mateu.ecdemo1.contenthub.domain.newsletter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * What an issue gets by itself: the items published since the previous issue was opened — the site's own,
 * in each of its sections, and the intranet's, in the sections mapped to them — seen by someone of the
 * newsletter's audience and not expired; newest first, a few per section.
 */
public final class AutomaticContent {

    public static final int PER_SECTION = 8;

    /** A published item, as far as choosing goes. */
    public record Candidate(String itemId, String siteId, String sectionId, Instant publishedAt, Instant expireAt) {
    }

    private AutomaticContent() {
    }

    /** Where the window starts: when the previous issue was opened, or — for the first — a cadence period ago. */
    public static Instant since(NewsletterIssue previous, Cadence cadence, Instant now) {
        return previous != null && previous.createdAt() != null ? previous.createdAt() : now.minus(cadence.period());
    }

    /**
     * The items for each of the site's sections, in its order.
     *
     * @param siteSections      the newsletter site's sections
     * @param ancestors         an intranet section's ancestors (a mapped section takes its subsections' items)
     * @param seenByTheAudience whether someone of the newsletter's audience sees the item
     */
    public static Map<String, List<String>> pick(Newsletter newsletter, List<String> siteSections,
                                                 List<Candidate> candidates, Instant since, Instant now,
                                                 Function<String, List<String>> ancestors,
                                                 Predicate<String> seenByTheAudience) {
        var out = new LinkedHashMap<String, List<String>>();
        siteSections.forEach(s -> out.put(s, new ArrayList<>()));
        candidates.stream()
                .filter(c -> c.publishedAt() != null && c.publishedAt().isAfter(since) && !c.publishedAt().isAfter(now))
                .filter(c -> c.expireAt() == null || c.expireAt().isAfter(now))
                .sorted(Comparator.comparing(Candidate::publishedAt).reversed())
                .forEach(c -> {
                    var own = newsletter.id().equals(c.siteId());
                    newsletter.sectionFor(own, c.sectionId(), own ? List.of() : ancestors.apply(c.sectionId()), siteSections)
                            .ifPresent(key -> {
                                var list = out.get(key);
                                if (list.size() < PER_SECTION && seenByTheAudience.test(c.itemId())) {
                                    list.add(c.itemId());
                                }
                            });
                });
        return out;
    }
}
