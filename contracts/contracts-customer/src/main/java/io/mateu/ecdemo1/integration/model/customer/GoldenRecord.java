package io.mateu.ecdemo1.integration.model.customer;

import java.time.LocalDate;
import java.util.List;

/**
 * A customer's data as the MDM holds it: Salesforce's, with the MDM's survivorship after a merge.
 * {@code documentType}/{@code documentNumber} are the customer's main document — the last one verified —
 * and {@code documents} every document the MDM knows of them (a DNI and a passport, say), main one
 * included; null when the producer does not say.
 */
public record GoldenRecord(String firstName, String lastName, String email, String phone, String nationality,
                           LocalDate birthDate, String documentType, String documentNumber,
                           List<IdentityDocument> documents, Profile profile) {

    /** Without the list of documents: only the main one is said. */
    public GoldenRecord(String firstName, String lastName, String email, String phone, String nationality,
                        LocalDate birthDate, String documentType, String documentNumber) {
        this(firstName, lastName, email, phone, nationality, birthDate, documentType, documentNumber, null, null);
    }

    /** Without the kárdex profile. */
    public GoldenRecord(String firstName, String lastName, String email, String phone, String nationality,
                        LocalDate birthDate, String documentType, String documentNumber,
                        List<IdentityDocument> documents) {
        this(firstName, lastName, email, phone, nationality, birthDate, documentType, documentNumber, documents, null);
    }

    public String fullName() {
        return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    }

    /**
     * One of a customer's identity documents.
     *
     * @param type           DNI, PASSPORT, ID_CARD, RESIDENCE or OTHER, as read
     * @param number         as read
     * @param issuingCountry ISO 3166-1 alpha-2; null when unknown
     */
    public record IdentityDocument(String type, String number, String issuingCountry) {
    }

    /**
     * What the guest declared of themselves at a hotel's desk (their kárdex), as the MDM keeps it; null
     * fields are unknown. {@code marketingConsent} null: never asked.
     */
    public record Profile(String sex, String language, String birthPlace, String address, String city,
                          String postalCode, String province, String countryOfResidence, String fax,
                          String riuClass, Boolean marketingConsent) {
    }
}
