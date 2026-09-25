package io.mateu.ecdemo1.integration.model.partner;

/**
 * A trading partner — agency, tour operator, online agency, company — as the master of partners
 * holds it. A master, not a transaction: one partner appears in thousands of reservations.
 *
 * @param billingMode who pays the stay: the guest at the desk (FRONT) or the partner (NO_FRONT),
 *                    which decides how the PMS routes charges between folio windows
 * @param pmsProfileId which profile it is in the PMS, as the master records it; null until it is one
 *                     there — then, and only then, the integration creates it
 * @param pmsProfileType that profile's type in the PMS (Agent, Company, Source)
 */
public record Partner(String code,
                      PartnerType type,
                      String name,
                      String taxId,
                      Address address,
                      String email,
                      String phone,
                      BillingMode billingMode,
                      boolean active,
                      long version,
                      String pmsProfileId,
                      String pmsProfileType) {

    public Partner(String code, PartnerType type, String name, String taxId, Address address, String email, String phone,
                   BillingMode billingMode, boolean active, long version) {
        this(code, type, name, taxId, address, email, phone, billingMode, active, version, null, null);
    }
}
