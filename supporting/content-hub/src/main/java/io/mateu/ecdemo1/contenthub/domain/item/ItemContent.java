package io.mateu.ecdemo1.contenthub.domain.item;

import io.mateu.ecdemo1.contenthub.domain.audience.Audience;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything about an item that is edited and published together: what a version freezes.
 *
 * @param publishAt             when it becomes visible; null = the moment it is published
 * @param expireAt              when it stops being visible; null = never
 * @param readTracking          whether reads are recorded
 * @param confirmationRequired  whether every reader must confirm having read it
 * @param confirmationDeadline  by when, when it must be confirmed
 * @param url                   LINK: where it goes; DOCUMENT: the file
 */
public record ItemContent(String sectionId, Audience audience, Map<String, Translation> translations,
                          List<Block> blocks, Instant publishAt, Instant expireAt, boolean readTracking,
                          boolean confirmationRequired, Instant confirmationDeadline, String url) {

    public ItemContent {
        audience = audience == null ? Audience.EVERYONE : audience;
        translations = translations == null ? Map.of() : Map.copyOf(new TreeMap<>(translations));
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    public ItemContent withPublishAt(Instant at) {
        return new ItemContent(sectionId, audience, translations, blocks, at, expireAt, readTracking,
                confirmationRequired, confirmationDeadline, url);
    }

    /** The text in a language, falling back to the default language's. */
    public Translation in(String language, String defaultLanguage) {
        var t = translations.get(language);
        return t != null ? t : translations.getOrDefault(defaultLanguage, new Translation("", "", ""));
    }
}
