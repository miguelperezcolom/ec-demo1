package io.mateu.ecdemo1.integrations.store;

import io.mateu.ecdemo1.integration.model.integration.IntegrationStatus;

import java.util.EnumSet;
import java.util.Set;

import static io.mateu.ecdemo1.integration.model.integration.IntegrationStatus.*;

/**
 * The integration's life as a state machine: every way its status can change, from which statuses,
 * and what is said when it is asked from any other. {@link Integration#apply} is the only place the
 * status changes, and it goes through here — so a move nobody listed is refused, not made.
 *
 * <p>The onboarding moves forward gate by gate, and back only where the world outside moves back:
 * a connection that fails, a property whose catalogue is found empty again. A decommissioned
 * integration is done: nothing leaves it.
 */
public enum IntegrationTransition {

    /** Opera does not take the connection it was registered with. */
    CONNECTIVITY_FAILS(EnumSet.of(CREATED), CONNECTIVITY_FAILED,
            "Only a new integration can fail its connection; it is %s"),
    /** Its connection, fixed, works: the onboarding goes on from the start. */
    CONNECTIVITY_RESTORED(EnumSet.of(CONNECTIVITY_FAILED), CREATED,
            "Only an integration whose connection failed can have it restored; it is %s"),
    /** Its property lacks the codes the CRS needs in Opera — found on contrasting its catalogue, even again. */
    PROPERTY_UNCONFIGURED(EnumSet.of(CREATED, MAPPING_PENDING), PENDING_CONFIGURATION,
            "The property's configuration is waited for while the catalogues are contrasted; the integration is %s"),
    /** The catalogues are contrasted and the property is configured: what is left is the mapping. */
    AWAIT_MAPPING(EnumSet.of(CREATED, PENDING_CONFIGURATION), MAPPING_PENDING,
            "The mapping is waited for once the catalogues are contrasted; the integration is %s"),
    SYNC_PARTNERS(EnumSet.of(MAPPING_PENDING), SYNCING_PARTNERS,
            "The partners are synced once the mapping is done; the integration is %s"),
    BLOCK_BACKFILL(EnumSet.of(SYNCING_PARTNERS), BACKFILL_BLOCKED,
            "The backfill is held by its gaps after the partners are synced; the integration is %s"),
    START_BACKFILL(EnumSet.of(SYNCING_PARTNERS, BACKFILL_BLOCKED), BACKFILLING,
            "The onboarding's backfill starts once its pre-pass is clear; the integration is %s"),
    WINDOW_COVERED(EnumSet.of(BACKFILLING), READY_TO_ACTIVATE,
            "Only a backfilling integration can be ready to activate; it is %s"),
    ACTIVATE(EnumSet.of(READY_TO_ACTIVATE), ACTIVE,
            "Only an integration ready to activate can be activated; it is %s"),
    PAUSE(EnumSet.of(ACTIVE), PAUSED,
            "Only an active integration can be paused; it is %s"),
    RESUME(EnumSet.of(PAUSED), ACTIVE,
            "Only a paused integration can be resumed; it is %s"),
    /** From anywhere but the end: a half-done onboarding can be abandoned, a running integration taken down. */
    DECOMMISSION(EnumSet.complementOf(EnumSet.of(DECOMMISSIONED)), DECOMMISSIONED,
            "The integration is already %s");

    /** Where every integration starts. */
    public static final IntegrationStatus INITIAL = CREATED;

    final Set<IntegrationStatus> from;
    final IntegrationStatus to;
    final String refusal;

    IntegrationTransition(Set<IntegrationStatus> from, IntegrationStatus to, String refusal) {
        this.from = from;
        this.to = to;
        this.refusal = refusal;
    }

    public IntegrationStatus to() {
        return to;
    }

    public boolean allowedFrom(IntegrationStatus status) {
        return status != null && from.contains(status);
    }

    /** The status it leads to from {@code status}; refused, with what a person reads, from any other. */
    public IntegrationStatus from(IntegrationStatus status) {
        if (!allowedFrom(status)) {
            throw new IllegalStateException(refusal.formatted(status));
        }
        return to;
    }
}
