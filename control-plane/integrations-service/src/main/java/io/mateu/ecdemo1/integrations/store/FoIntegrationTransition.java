package io.mateu.ecdemo1.integrations.store;

import java.util.EnumSet;
import java.util.Set;

import static io.mateu.ecdemo1.integrations.store.FoIntegrationStatus.*;

/**
 * The pms-fo integration's life as a state machine, as {@link IntegrationTransition} is the crs-pms
 * one's: every way its status can change, from which statuses, and what is said when it is asked from
 * any other. {@link FrontOfficeIntegration#apply} goes through here, so a move nobody listed is refused.
 */
public enum FoIntegrationTransition {

    /** The PMS or the front office does not answer the first try. */
    CONNECTIVITY_FAILS(EnumSet.of(CREATED), CONNECTIVITY_FAILED,
            "Only a new pms-fo integration can fail its connection; it is %s"),
    /** Both answer now: the onboarding goes on from the start. */
    CONNECTIVITY_RESTORED(EnumSet.of(CONNECTIVITY_FAILED), CREATED,
            "Only a pms-fo integration whose connection failed can have it restored; it is %s"),
    /** The PMS's catalogue is sent to the front office. */
    SYNC_CATALOGUE(EnumSet.of(CREATED), SYNCING_CATALOGUE,
            "The catalogue is synced once the connections work; the pms-fo integration is %s"),
    START_BACKFILL(EnumSet.of(SYNCING_CATALOGUE), BACKFILLING,
            "The backfill starts once the front office has the catalogue; the pms-fo integration is %s"),
    BACKFILL_DONE(EnumSet.of(BACKFILLING), READY_TO_ACTIVATE,
            "Only a backfilling pms-fo integration can be ready to activate; it is %s"),
    ACTIVATE(EnumSet.of(READY_TO_ACTIVATE), ACTIVE,
            "Only a pms-fo integration ready to activate can be activated; it is %s"),
    PAUSE(EnumSet.of(ACTIVE), PAUSED,
            "Only an active pms-fo integration can be paused; it is %s"),
    RESUME(EnumSet.of(PAUSED), ACTIVE,
            "Only a paused pms-fo integration can be resumed; it is %s"),
    DECOMMISSION(EnumSet.complementOf(EnumSet.of(DECOMMISSIONED)), DECOMMISSIONED,
            "The pms-fo integration is already %s");

    public static final FoIntegrationStatus INITIAL = CREATED;

    final Set<FoIntegrationStatus> from;
    final FoIntegrationStatus to;
    final String refusal;

    FoIntegrationTransition(Set<FoIntegrationStatus> from, FoIntegrationStatus to, String refusal) {
        this.from = from;
        this.to = to;
        this.refusal = refusal;
    }

    public FoIntegrationStatus to() {
        return to;
    }

    public boolean allowedFrom(FoIntegrationStatus status) {
        return status != null && from.contains(status);
    }

    public FoIntegrationStatus from(FoIntegrationStatus status) {
        if (!allowedFrom(status)) {
            throw new IllegalStateException(refusal.formatted(status));
        }
        return to;
    }
}
