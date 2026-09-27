package io.mateu.ecdemo1.booking.application.out.inbox;

/**
 * Where a consumer deduplicates what it receives. Delivery is at-least-once, so a consumer records
 * each message id in the same transaction as what it does about it, and does nothing with one it has
 * already recorded.
 */
public interface Inbox {

    /** @return true the first time this consumer sees the message, false on every repetition */
    boolean firstTime(String consumer, String messageId);
}
