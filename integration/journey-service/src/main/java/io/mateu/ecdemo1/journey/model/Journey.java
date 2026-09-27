package io.mateu.ecdemo1.journey.model;

import java.time.Duration;
import java.util.List;

/**
 * One change of a booking — its creation, a modification, a cancellation — followed across the
 * chain: every hop in business words, and what the business asks of it (how long until Opera had
 * it, how long until the front office did).
 *
 * @param version        the CRS version the change carried, when known
 * @param crsToOpera     from the change in the CRS to Opera having it written; null if it did not get there
 * @param crsToFrontOffice from the change in the CRS to the front office having the stay; null likewise
 */
public record Journey(String traceId, String locator, String hotel, Kind kind, Long version,
                      long startNanos, long endNanos, Duration crsToOpera, Duration crsToFrontOffice,
                      Outcome outcome, String outcomeDetail, String operaHotel, String operaReservationId,
                      String operaAction, List<ProcessRef> processes, List<Hop> hops, List<String> translations,
                      List<String> causes, List<Identity> identities) {

    public Duration total() {
        return Duration.ofNanos(Math.max(0, endNanos - startNanos));
    }

    /** The lanes this change went through, in the story's order. */
    public List<Lane> lanes() {
        return hops.stream().map(Hop::lane).distinct().sorted().toList();
    }
}
