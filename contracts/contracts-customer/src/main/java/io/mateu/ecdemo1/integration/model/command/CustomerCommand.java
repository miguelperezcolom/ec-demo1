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
     */
    record RecordScannedIdentity(String commandId, String hotelCode, String locator, String stayId, int pax,
                                 String customerId, String firstName, String lastName, String documentType,
                                 String documentNumber, LocalDate birthDate, String nationality, String origin)
            implements CustomerCommand {
        @Override
        public String key() {
            return customerId != null && !customerId.isBlank() ? customerId : hotelCode + "/" + locator;
        }
    }
}
