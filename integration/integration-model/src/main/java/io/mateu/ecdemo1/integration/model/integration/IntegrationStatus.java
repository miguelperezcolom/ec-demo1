package io.mateu.ecdemo1.integration.model.integration;

/**
 * Where a hotel's integration is in its life (HLA F010, «Alta de Integración»). Nothing flows to the
 * PMS until {@link #ACTIVE} — except the backfill, which is how the property gets ready — so a
 * half-onboarded integration is latent and harmless, not something to undo.
 */
public enum IntegrationStatus {
    /** Registered; its connection to the PMS is being checked. */
    CREATED,
    /** The PMS did not take its connection data or credentials. Fixed by editing them. */
    CONNECTIVITY_FAILED,
    /** The property lacks codes in the PMS that the CRS needs. Configuring it is outside the integration (R10). */
    PENDING_CONFIGURATION,
    /** Waiting for a person to approve the mapping of the hotel's codes. */
    MAPPING_PENDING,
    /** The partners the reservations reference are being projected as PMS profiles. */
    SYNCING_PARTNERS,
    /** The codes and partners the future reservations use have gaps; the backfill will not start with them. */
    BACKFILL_BLOCKED,
    /** Projecting the hotel's future reservations, nearest arrival first. */
    BACKFILLING,
    /** The backfill has covered the activation window: a person can switch real-time traffic on. */
    READY_TO_ACTIVATE,
    /** Real-time traffic flows. */
    ACTIVE,
    /** Real-time traffic waits, and resumes on its own when the integration is resumed. */
    PAUSED,
    /** Taken down. What reached the PMS stays there (R5). */
    DECOMMISSIONED;

    /** Whether reservations of the hotel reach the PMS in real time. */
    public boolean flows() {
        return this == ACTIVE;
    }
}
