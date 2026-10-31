package io.mateu.ecdemo1.contenthub.application.out;

import java.time.Instant;

/** A published item as a reader gets it: in their language, or the default one when theirs is missing. */
public record VisibleItem(String itemId, int version, String type, String sectionId, String language, String title,
                          String summary, String body, String url, String author, Instant publishAt,
                          Instant expireAt, boolean readTracking, boolean confirmationRequired,
                          Instant confirmationDeadline, int confirmationVersion) {
}
