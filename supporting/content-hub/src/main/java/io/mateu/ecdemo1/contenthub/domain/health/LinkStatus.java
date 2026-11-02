package io.mateu.ecdemo1.contenthub.domain.health;

import java.time.Duration;
import java.time.Instant;

/**
 * What an external URL answered when it was last checked — kept, so a URL is not asked again for a while.
 *
 * @param httpStatus the last status seen (after redirects); 0 when there was no answer
 * @param detail     why, for the editor: "HTTP 404", "sin respuesta en 5 s", "el dominio no existe"…
 */
public record LinkStatus(String url, Outcome outcome, int httpStatus, String detail, Instant checkedAt) {

    public enum Outcome {
        /** It answers (2xx, or 401/403: it exists, behind a login). */
        OK,
        /** It does not exist or does not answer. */
        BROKEN,
        /** Not checked on purpose: a private address, a scheme that is not http(s). */
        SKIPPED,
        /** Not checked yet (this run's budget was spent) or it said "later" (429): no finding. */
        UNKNOWN
    }

    public static LinkStatus unknown(String url) {
        return new LinkStatus(url, Outcome.UNKNOWN, 0, "aún sin comprobar", null);
    }

    /** Whether it can be used instead of asking again: a good answer lasts longer than a bad one. */
    public boolean freshAt(Instant now, Duration okFor, Duration otherwiseFor) {
        if (checkedAt == null || outcome == Outcome.UNKNOWN) {
            return false;
        }
        return checkedAt.plus(outcome == Outcome.OK ? okFor : otherwiseFor).isAfter(now);
    }
}
