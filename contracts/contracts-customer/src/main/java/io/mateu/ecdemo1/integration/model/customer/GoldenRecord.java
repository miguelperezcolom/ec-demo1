package io.mateu.ecdemo1.integration.model.customer;

import java.time.LocalDate;

/** A customer's data as the MDM holds it: Salesforce's, with the MDM's survivorship after a merge. */
public record GoldenRecord(String firstName, String lastName, String email, String phone, String nationality,
                           LocalDate birthDate, String documentType, String documentNumber) {

    public String fullName() {
        return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    }
}
