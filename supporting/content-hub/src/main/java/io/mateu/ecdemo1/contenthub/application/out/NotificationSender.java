package io.mateu.ecdemo1.contenthub.application.out;

import java.time.Instant;

/**
 * Where a reader's notification leaves for the platform's bell and Web Push: communication-service,
 * through the outbox, in the caller's transaction.
 */
public interface NotificationSender {

    /**
     * For one reader, in their language.
     *
     * @param version the item's version; for a reminder, the confirmation's
     * @param link    where to read it
     */
    record ReaderNotification(NotificationLog.Kind kind, String itemId, int version, String userId, String title,
                              String body, String link, Instant at) {

        /** The same notification twice is one: per item version, kind and reader. */
        public String dedupKey() {
            return "intranet:" + kind.name().toLowerCase() + ":" + itemId + ":v" + version + ":" + userId;
        }

        public String subject() {
            return NotificationSender.subject(kind, itemId, userId);
        }
    }

    /** What resolving closes it by: not the version, so reading an item closes all of its notices to that reader. */
    static String subject(NotificationLog.Kind kind, String itemId, String userId) {
        return "intranet/" + (kind == NotificationLog.Kind.PUBLISHED ? "item/" : "confirmation/") + itemId + "/" + userId;
    }

    void send(ReaderNotification notification);

    void resolve(String subject, String resolvedBy, Instant at);
}
