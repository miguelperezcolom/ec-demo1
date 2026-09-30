package io.mateu.ecdemo1.integration.model.notification;

public enum NotificationType {
    /** A new cause has processes waiting on it. */
    CAUSE_OPENED,
    /** The agent has proposed mappings that wait for a person to review them. */
    PROPOSAL_READY,
    /** A write to the PMS keeps failing and is still being retried. */
    RETRYING_TOO_LONG,
    /** The PMS refused a write in a way retrying will not fix. */
    PMS_REJECTED,
    /** A hotel's onboarding stopped at a gate a person has to open: credentials, configuration, gaps, activation. */
    INTEGRATION_NEEDS_ATTENTION,
    /**
     * A stay the front desk let in with a forced check-in still lacks a guest's document past the
     * traveller's-registration deadline (24 h from the arrival): reception must complete it.
     */
    CHECK_IN_INCOMPLETE,
    /**
     * The platform itself: a Prometheus alert fired — an external API degraded, a service down.
     * Alertmanager's, not an integration's: it arrives on communication-service's webhook, not on the
     * notifications topic.
     */
    PLATFORM_ALERT,
    /** As {@link #PLATFORM_ALERT}, for an alert of severity critical: Opera or Salesforce not answering, a pod down. */
    PLATFORM_ALERT_CRITICAL
}
