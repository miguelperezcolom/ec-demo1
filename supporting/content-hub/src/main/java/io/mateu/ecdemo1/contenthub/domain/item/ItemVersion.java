package io.mateu.ecdemo1.contenthub.domain.item;

import java.time.Instant;

/** A publication, frozen: what readers see until the next one. Never changed once made. */
public record ItemVersion(int number, ItemContent content, Instant publishedAt, String publishedBy) {
}
