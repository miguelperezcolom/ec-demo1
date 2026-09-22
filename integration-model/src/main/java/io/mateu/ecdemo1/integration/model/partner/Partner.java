package io.mateu.ecdemo1.integration.model.partner;

/**
 * A trading partner — agency, tour operator, online agency, company — as the master of partners
 * holds it. A master, not a transaction: one partner appears in thousands of reservations.
 *
 * @param billingMode who pays the stay: the guest at the desk (FRONT) or the partner (NO_FRONT),
 *                    which decides how the PMS routes charges between folio windows
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
                      long version) {
}
