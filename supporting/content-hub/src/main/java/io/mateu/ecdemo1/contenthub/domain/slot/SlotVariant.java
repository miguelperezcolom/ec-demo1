package io.mateu.ecdemo1.contenthub.domain.slot;

import io.mateu.ecdemo1.contenthub.domain.audience.Audience;

/**
 * One version of a slot's content, for an audience. The reader gets the visible variant with the highest
 * priority; none visible, the application draws its fallback.
 */
public record SlotVariant(String id, Audience audience, int priority, String title, String html, String linkUrl) {

    public SlotVariant {
        audience = audience == null ? Audience.EVERYONE : audience;
    }
}
