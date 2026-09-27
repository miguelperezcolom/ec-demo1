package io.mateu.ecdemo1.journey.application;

import java.time.Instant;

/** A booking with traces lately: the list the journey screen opens on. */
public record RecentBooking(String locator, String hotel, Instant lastChange, int changes, String lastChangeKind,
                            long lastDurationMillis) {
}
