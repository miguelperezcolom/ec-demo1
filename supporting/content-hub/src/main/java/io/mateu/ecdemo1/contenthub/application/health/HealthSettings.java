package io.mateu.ecdemo1.contenthub.application.health;

import java.time.Duration;
import java.util.Set;

/**
 * How «Salud del contenido» judges ({@code content-hub.health.*}).
 *
 * @param expiryDays         a published item expiring within this many days is a finding (14)
 * @param staleMonths        a published item not republished in this many months is stale (12)
 * @param staleSections      the sections where freshness matters; empty: all
 * @param unviewedDays       a page nobody opened in this many days is a finding (90), if analytics cover them
 * @param externalLinks      whether external URLs are checked at all
 * @param linkTimeout        per request
 * @param perHostDelay       the least time between two requests to the same host
 * @param maxExternalPerRun  how many URLs one run asks at most; the rest wait for the next run
 * @param okFor              how long a good answer is trusted
 * @param brokenFor          how long a bad (or skipped) answer is trusted
 * @param userAgent          who is asking, said honestly, with where to read more
 * @param orphanGrace        a medium uploaded less than this ago is not an orphan yet (it is about to be used)
 */
public record HealthSettings(int expiryDays, int staleMonths, Set<String> staleSections, int unviewedDays,
                             boolean externalLinks, Duration linkTimeout, Duration perHostDelay, int maxExternalPerRun,
                             Duration okFor, Duration brokenFor, String userAgent, Duration orphanGrace) {

    public static final String USER_AGENT = "ec-demo1-content-health/1.0 (link checker of the intranet; HEAD first, one request per host per second)";

    public HealthSettings {
        staleSections = staleSections == null ? Set.of() : Set.copyOf(staleSections);
        userAgent = userAgent == null || userAgent.isBlank() ? USER_AGENT : userAgent;
    }

    public static HealthSettings defaults() {
        return new HealthSettings(14, 12, Set.of(), 90, true, Duration.ofSeconds(5), Duration.ofSeconds(1), 300,
                Duration.ofDays(7), Duration.ofDays(1), USER_AGENT, Duration.ofDays(1));
    }

    public boolean freshnessMatters(String sectionId) {
        return staleSections.isEmpty() || (sectionId != null && staleSections.contains(sectionId));
    }
}
