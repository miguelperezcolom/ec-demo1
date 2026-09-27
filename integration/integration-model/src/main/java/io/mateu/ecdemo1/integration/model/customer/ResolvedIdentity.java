package io.mateu.ecdemo1.integration.model.customer;

/**
 * One passenger's customer: its golden id, the true customer code that travels to the PMS as the
 * profile's CRM reference.
 *
 * @param passenger the passenger's position in the request
 * @param status    PROVISIONAL when nothing matched and cleaning is pending; CONSOLIDATED once it
 *                  survived a merge
 * @param matchedBy what identified it: SOURCE (this passenger of this reservation, asked before),
 *                  DOCUMENT, EMAIL, or NEW
 */
public record ResolvedIdentity(int passenger, String customerId, CustomerStatus status, String matchedBy) {
}
