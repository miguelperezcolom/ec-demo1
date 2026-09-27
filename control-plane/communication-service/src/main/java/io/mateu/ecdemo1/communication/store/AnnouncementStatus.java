package io.mateu.ecdemo1.communication.store;

/** Where one inbox item stands on one channel. Its own enum: its table is new, its check constraint too. */
public enum AnnouncementStatus {
    PENDING,
    SENT,
    /** Refused or unreachable; tried again after a wait, until the attempts run out. */
    FAILED,
    /** The browser's subscription no longer exists (404/410): dropped, never tried again. */
    GONE,
    /** Resolved before it went: nobody needs to be told any more. */
    SKIPPED
}
