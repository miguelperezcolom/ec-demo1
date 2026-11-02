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
 * @param notifyReaders         whether its readers are notified when it becomes effective; null = as its
 *                              type says ({@link #notifies(ItemType)}: notices yes, the rest no)
 * @param important             whether that notification also goes by e-mail (to readers who allow it)
 */
public record ItemContent(String sectionId, Audience audience, Map<String, Translation> translations,
                          List<Block> blocks, Instant publishAt, Instant expireAt, boolean readTracking,
                          boolean confirmationRequired, Instant confirmationDeadline, String url,
                          Boolean notifyReaders, boolean important) {

    public ItemContent {
        audience = audience == null ? Audience.EVERYONE : audience;
        translations = translations == null ? Map.of() : Map.copyOf(new TreeMap<>(translations));
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    /** Without a say on notifications: as the type says, and not important. */
    public ItemContent(String sectionId, Audience audience, Map<String, Translation> translations,
                       List<Block> blocks, Instant publishAt, Instant expireAt, boolean readTracking,
                       boolean confirmationRequired, Instant confirmationDeadline, String url) {
        this(sectionId, audience, translations, blocks, publishAt, expireAt, readTracking, confirmationRequired,
                confirmationDeadline, url, null, false);
    }

    public ItemContent withPublishAt(Instant at) {
        return new ItemContent(sectionId, audience, translations, blocks, at, expireAt, readTracking,
                confirmationRequired, confirmationDeadline, url, notifyReaders, important);
    }

    public ItemContent withNotification(Boolean notifyReaders, boolean important) {
        return new ItemContent(sectionId, audience, translations, blocks, publishAt, expireAt, readTracking,
                confirmationRequired, confirmationDeadline, url, notifyReaders, important);
    }

    /**
     * Whether an item of this type with this content notifies its readers: what the editor chose, or by
     * default only a notice — a notice is what someone must know; news can wait for the reader.
     */
    public boolean notifies(ItemType type) {
        return notifyReaders != null ? notifyReaders : type == ItemType.NOTICE;
    }

    /**
     * The item's audience as the visibility function projects it: the editor's, ANDed — for a remote page —
     * with who may use the UI source it shows ({@link io.mateu.ecdemo1.contenthub.domain.uisource.RemoteUi}).
     */
    public Audience visibilityAudience(ItemType type) {
        if (type != ItemType.REMOTE_PAGE) {
            return audience;
        }
        return io.mateu.ecdemo1.contenthub.domain.uisource.RemoteUi.parse(url)
                .map(r -> Audience.effective(audience, r.audienceReference())).orElse(audience);
    }

    /** The text in a language, falling back to the default language's. */
    public Translation in(String language, String defaultLanguage) {
        var t = translations.get(language);
        return t != null ? t : translations.getOrDefault(defaultLanguage, new Translation("", "", ""));
    }
}
