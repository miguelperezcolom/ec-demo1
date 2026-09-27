package io.mateu.ecdemo1.journey.tempo;

import java.time.Instant;
import java.util.Map;

/**
 * A trace as Tempo's search lists it: enough to order a booking's changes and to fetch each one.
 *
 * @param matched the attributes of the spans that matched the search (booking.locator, hotel.code)
 */
public record TraceSummary(String traceId, Instant start, long durationMillis, String rootService, String rootName,
                           Map<String, String> matched) {
}
