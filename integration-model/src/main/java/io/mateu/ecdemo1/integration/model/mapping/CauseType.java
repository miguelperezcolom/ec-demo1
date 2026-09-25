package io.mateu.ecdemo1.integration.model.mapping;

/** Why a process is waiting. Every kind is resolved by something other than retrying. */
public enum CauseType {
    /** A CRS code with no approved equivalent in the PMS. */
    MISSING_MAPPING,
    /** A partner the reservation references that is not yet a profile in the PMS. */
    MISSING_PARTNER,
    /** A cancellation for a reservation that has not reached the PMS yet. */
    NOT_YET_PROJECTED,
    /** The PMS refused the write, and would refuse it again: someone has to look. */
    PMS_REJECTED,
    /** The hotel's integration is not active: onboarding, paused, or not there at all. */
    INTEGRATION_INACTIVE
}
