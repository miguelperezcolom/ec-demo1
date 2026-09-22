package io.mateu.ecdemo1.integration.model.notification;

public enum NotificationType {
    /** A new cause has processes waiting on it. */
    CAUSE_OPENED,
    /** The agent has proposed mappings that wait for a person to review them. */
    PROPOSAL_READY,
    /** A write to the PMS keeps failing and is still being retried. */
    RETRYING_TOO_LONG,
    /** The PMS refused a write in a way retrying will not fix. */
    PMS_REJECTED
}
