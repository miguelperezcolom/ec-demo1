package io.mateu.ecdemo1.communication.store;

public enum DeliveryStatus {
    SENT,
    /** Sending failed; tried again until it goes or the attempts run out. */
    FAILED,
    /** No recipient wants this kind, for this hotel. Kept, so the gap shows. */
    NO_RECIPIENTS,
    /** Somebody wants it, and nobody by e-mail: it went to inboxes, chat spaces or browsers only. */
    INBOX_ONLY
}
