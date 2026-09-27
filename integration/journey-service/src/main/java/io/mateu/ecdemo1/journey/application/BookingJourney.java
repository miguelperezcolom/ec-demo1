package io.mateu.ecdemo1.journey.application;

import io.mateu.ecdemo1.journey.business.BusinessData;
import io.mateu.ecdemo1.journey.model.Journey;

import java.time.Instant;
import java.util.List;

/**
 * Everything the screen shows of one booking: each change's journey, newest first, and what the
 * systems say about it now.
 *
 * @param unreadTraces traces Tempo listed but could not hand over yet (still being ingested)
 * @param problem      why nothing could be read, when Tempo itself did not answer
 */
public record BookingJourney(String locator, String hotel, List<Journey> changes, BusinessData business,
                             int unreadTraces, String problem, Instant readAt) {

    public boolean empty() {
        return changes.isEmpty();
    }

    /** The newest change, or the one whose trace id is given. */
    public Journey change(String traceId) {
        if (changes.isEmpty()) {
            return null;
        }
        if (traceId != null) {
            for (var change : changes) {
                if (change.traceId().equals(traceId)) {
                    return change;
                }
            }
        }
        return changes.get(0);
    }

    /** Its number among the booking's changes, oldest first: 1 is the first one the traces know. */
    public int ordinal(Journey change) {
        return changes.size() - changes.indexOf(change);
    }
}
