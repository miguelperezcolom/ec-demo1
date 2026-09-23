package io.mateu.ecdemo1.integration.model.partner;

/**
 * A partner as the PMS has it, when the PMS — not the ERP — is where partners are kept: read from it
 * and imported into the master of partners.
 *
 * @param code         the partner's code in the chain: OPERA's CorporateId, which is also its code in
 *                     the ERP
 * @param pmsProfileId the profile reservations reference it by
 * @param profileType  OPERA's profile type: Agent, Company or Source
 */
public record PmsPartner(String code, String pmsProfileId, String profileType, String name) {
}
