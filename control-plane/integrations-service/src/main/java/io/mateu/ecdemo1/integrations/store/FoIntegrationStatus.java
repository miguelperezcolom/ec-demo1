package io.mateu.ecdemo1.integrations.store;

/**
 * Where a hotel's pms-fo integration is in its life: its front office fed from the PMS. Its own, not
 * the crs-pms integration's — the onboarding is shorter: a connection to both sides, the PMS's
 * catalogue into the front office, the backfill of the property's reservations, and a person's
 * activation.
 */
public enum FoIntegrationStatus {
    /** Registered: the connections to the PMS and to the front office are being tried. */
    CREATED,
    /** The PMS or the front office does not answer: it goes on once both do. */
    CONNECTIVITY_FAILED,
    /** The PMS's catalogue is on its way to the front office. */
    SYNCING_CATALOGUE,
    /** The property's reservations are being projected into the front office, a batch at a time. */
    BACKFILLING,
    /** The front office holds the property's reservations: a person switches the changes on. */
    READY_TO_ACTIVATE,
    /** Every change in the PMS reaches the front office: as it is written, and by polling. */
    ACTIVE,
    /** Nothing reaches the front office until it is resumed; the polling picks up where it stopped. */
    PAUSED,
    /** Done: nothing leaves it. */
    DECOMMISSIONED
}
