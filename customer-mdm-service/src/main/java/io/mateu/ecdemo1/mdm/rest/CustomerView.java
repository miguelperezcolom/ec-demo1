package io.mateu.ecdemo1.mdm.rest;

import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.SalesforceState;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A golden record as the MDM's consumers see it (HLA CRM-MDM, F010).
 *
 * @param requestedId  the code asked for — an absorbed one answers with its survivor
 * @param aliases      codes absorbed into this one by merges
 * @param reservations {@code hotel/locator} of the reservations its passengers came from
 */
public record CustomerView(String id, String requestedId, CustomerStatus status, String firstName, String lastName,
                           String email, String phone, String nationality, LocalDate birthDate, String documentType,
                           String documentNumber, long version, SalesforceState salesforceState,
                           String salesforceContactId, String survivorship, Instant updatedAt, List<String> aliases,
                           List<String> reservations, List<String> xrefs, List<String> changeRequests) {

    public static CustomerView of(Customer c, String requestedId, List<String> aliases, List<String> reservations) {
        return of(c, requestedId, aliases, reservations, List.of(), List.of());
    }

    /**
     * @param xrefs          where the customer is known: SYSTEM:reference (context)
     * @param changeRequests the changes hotels proposed: id STATUS — what changes
     */
    public static CustomerView of(Customer c, String requestedId, List<String> aliases, List<String> reservations,
                                  List<String> xrefs, List<String> changeRequests) {
        return new CustomerView(c.id, requestedId, c.status, c.firstName, c.lastName, c.email, c.phone, c.nationality,
                c.birthDate, c.documentType, c.documentNumber, c.version, c.salesforceState, c.salesforceContactId,
                c.survivorship, c.updatedAt, aliases, reservations, xrefs, changeRequests);
    }
}
