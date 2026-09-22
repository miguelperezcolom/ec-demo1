package io.mateu.ecdemo1.communication.store;

public enum DeliveryStatus {
    SENT,
    /** Sending failed; tried again until it goes or the attempts run out. */
    FAILED,
    /** Nobody is set up to receive this kind, for this hotel. Kept, so the gap shows. */
    NO_RECIPIENTS
}
