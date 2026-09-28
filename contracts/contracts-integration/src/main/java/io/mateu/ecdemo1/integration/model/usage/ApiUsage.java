package io.mateu.ecdemo1.integration.model.usage;

import java.time.Instant;
import java.util.Map;

/**
 * How much of an external API — Salesforce, Opera (OHIP) — the platform spends, as the service that
 * calls it counts it: its own calls in the last 24 hours, by what they were for, and what the API
 * itself says of its allowance when it says anything. Served at {@code GET /usage/{api}} by every
 * service that calls one, inside the cluster only; integrations-service adds them up for the
 * consoles' header.
 *
 * @param api         {@code salesforce} or {@code opera}
 * @param service     who counted it
 * @param orgUsed     the allowance used, as the API last said — for Salesforce, the org's calls in the
 *                    rolling 24 hours, whoever made them; null when the API does not say
 * @param orgMax      the allowance, when the API says it
 * @param seenAt      when the API last said it
 * @param ours24h     this service's calls in the last 24 hours (for Salesforce not counting the OAuth
 *                    token requests, which the org does not count either)
 * @param byPurpose   those calls by what they were for
 * @param byEndpoint  by endpoint, when the purpose is too coarse (Opera: the URI template); may be empty
 * @param others24h   what the org spent that is not ours ({@code orgUsed - ours24h}): scripts, other
 *                    environments sharing the org, people; null when the org total is unknown
 * @param limited24h  calls the API refused for its allowance or rate (Salesforce's
 *                    REQUEST_LIMIT_EXCEEDED, OHIP's 429)
 * @param errors24h   calls that failed otherwise
 * @param paused      whether the service is holding its calls because the allowance is spent
 * @param pausedUntil until when, if it is
 * @param rateLimit   the rate-limit headers the API last sent, as sent; empty if it sends none
 * @param rateLimitRemaining what the API last said remains of its rate limit, when it says a number
 * @param ours1h      this service's calls in the last 60 minutes (as ours24h: no tokens)
 * @param oursPrevious1h in the 60 minutes before those: with ours1h, whether we are speeding up
 * @param orgUsed1hAgo the org's total as it was said about an hour ago, if it was: with orgUsed,
 *                    whether the allowance is filling up or coming back
 */
public record ApiUsage(String api, String service, Long orgUsed, Long orgMax, Instant seenAt, long ours24h,
                       Map<String, Long> byPurpose, Map<String, Long> byEndpoint, Long others24h, long limited24h,
                       long errors24h, boolean paused, Instant pausedUntil, Map<String, String> rateLimit,
                       Long rateLimitRemaining, long ours1h, long oursPrevious1h, Long orgUsed1hAgo) {

    public ApiUsage {
        byPurpose = byPurpose == null ? Map.of() : byPurpose;
        byEndpoint = byEndpoint == null ? Map.of() : byEndpoint;
        rateLimit = rateLimit == null ? Map.of() : rateLimit;
    }

    /** What is left of the allowance, when the API says it: the figure to look at before a demo. */
    public Long orgLeft() {
        return orgUsed == null || orgMax == null ? null : Math.max(0, orgMax - orgUsed);
    }
}
