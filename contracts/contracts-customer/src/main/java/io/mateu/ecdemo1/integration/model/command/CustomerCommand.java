package io.mateu.ecdemo1.integration.model.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.LocalDate;

/**
 * What a hotel tells the customer MDM without waiting for an answer: sent through the hotel's outbox on
 * the {@code customer-commands} topic, and taken once — the MDM deduplicates on {@link #commandId()} in
 * its inbox. What the MDM decides comes back as it always does: on the {@code customers} topic.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = CustomerCommand.ProposeChange.class, name = "propose-change"),
        @JsonSubTypes.Type(value = CustomerCommand.RecordScannedIdentity.class, name = "record-scanned-identity"),
        @JsonSubTypes.Type(value = CustomerCommand.RecordKardex.class, name = "record-kardex"),
})
public sealed interface CustomerCommand {

    /** Unique per command; the MDM deduplicates on it. */
    String commandId();

    /** The Kafka key: what is said about one customer stays in order. */
    @JsonIgnore
    String key();

    /**
     * A change to a customer's data the desk made, for Salesforce — the master — to decide. The
     * {@code commandId} is also the change request's id: the decision comes back with it, and sending
     * it twice is one request. A null field is left as it is.
     */
    record ProposeChange(String commandId, String customerId, String name, String email, String phone,
                         String documentNumber, String origin) implements CustomerCommand {
        @Override
        public String key() {
            return customerId;
        }
    }

    /**
     * A pax's identity document, as the desk's scanner read it: trusted data. {@code customerId} is the
     * customer the hotel knows the pax by, if any; otherwise the MDM finds it by the reservation
     * ({@code hotelCode}, {@code locator}) and the pax (1 is the holder, 2… the companions in the room).
     *
     * <p>A document the customer did not have is added to theirs, not put in place of the one they had: the
     * same person shows a DNI on one trip and a passport on the next.
     *
     * @param issuingCountry      the country that issued the document (ISO 3166-1 alpha-2); null if the
     *                            scanner did not say — the MDM then takes the nationality
     * @param documentExpiry      null if the scanner did not say
     * @param confirmedCustomerId the known customer the desk confirmed this pax is — by their Riu Class
     *                            number or their email — when the hotel knew them by another code (a
     *                            provisional one): the MDM consolidates that code into this customer and
     *                            the document joins theirs. Null when nobody confirmed anything
     */
    record RecordScannedIdentity(String commandId, String hotelCode, String locator, String stayId, int pax,
                                 String customerId, String firstName, String lastName, String documentType,
                                 String documentNumber, LocalDate birthDate, String nationality, String origin,
                                 String issuingCountry, LocalDate documentExpiry, String confirmedCustomerId)
            implements CustomerCommand {

        /** Without issuing country, expiry or a confirmed customer. */
        public RecordScannedIdentity(String commandId, String hotelCode, String locator, String stayId, int pax,
                                     String customerId, String firstName, String lastName, String documentType,
                                     String documentNumber, LocalDate birthDate, String nationality, String origin) {
            this(commandId, hotelCode, locator, stayId, pax, customerId, firstName, lastName, documentType,
                    documentNumber, birthDate, nationality, origin, null, null, null);
        }

        @Override
        public String key() {
            return customerId != null && !customerId.isBlank() ? customerId : hotelCode + "/" + locator;
        }
    }

    /**
     * A pax's kárdex as the desk filled it in with the guest at the counter: what the guest declares of
     * themselves — sex, language, address, place of birth, fax, the document's issue date, their Riu Class
     * number, whether they accept advertising. The MDM keeps it on the customer the hotel knows the pax by
     * ({@code customerId}), or finds by the reservation and the pax as for a scan. A null field says
     * nothing; the name, email and phone still go as a {@link ProposeChange} (Salesforce decides them).
     *
     * @param sex                M, F or X
     * @param language           ISO 639-1 (es, en, de…)
     * @param countryOfResidence ISO 3166-1 alpha-2
     * @param documentIssueDate  the issue date of {@code documentNumber}, when the desk read it
     * @param riuClass           the guest's Riu Class member number, as they gave it
     * @param marketingConsent   whether they accept advertising; null when not asked
     * @param companion          whether the pax is a companion (pax 2…), not the holder
     */
    record RecordKardex(String commandId, String hotelCode, String locator, String stayId, int pax,
                        String customerId, String firstName, String lastName, String sex, LocalDate birthDate,
                        String birthPlace, String nationality, String language, String address, String city,
                        String postalCode, String province, String countryOfResidence, String fax,
                        String documentType, String documentNumber, LocalDate documentIssueDate,
                        LocalDate documentExpiry, String riuClass, Boolean marketingConsent, boolean companion,
                        String origin) implements CustomerCommand {

        @Override
        public String key() {
            return customerId != null && !customerId.isBlank() ? customerId : hotelCode + "/" + locator;
        }
    }
}
