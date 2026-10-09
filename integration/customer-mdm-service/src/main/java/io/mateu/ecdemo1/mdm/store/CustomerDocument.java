package io.mateu.ecdemo1.mdm.store;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

/**
 * One of a customer's identity documents. A person shows a DNI on one trip and a passport on the next,
 * and both are theirs: the customer's {@code documentType}/{@code documentNumber} stay the main one —
 * what Salesforce holds and survivorship decides — and every document the MDM has seen of them is here,
 * the main one included. What identifies a document is the number and the country that issued it, not
 * its type: the same number read as "DOC" or as "PASSPORT" is one document, and the same number issued
 * by two countries is two.
 */
@Entity
@Table(name = "customer_document", indexes = {
        @Index(name = "customer_document_country_number", columnList = "issuingCountry,numberKey"),
        @Index(name = "customer_document_number", columnList = "numberKey"),
        @Index(name = "customer_document_customer", columnList = "customerId")})
public class CustomerDocument {

    public enum Type { DNI, PASSPORT, ID_CARD, RESIDENCE, OTHER }

    /** Where the MDM learnt it: a scan at the desk, a reservation, or the customer's own data (Salesforce, a merge). */
    public enum Origin { SCAN, RESERVATION, CUSTOMER }

    @Id
    public String id;
    public String customerId;
    /** One of {@link Type}. */
    public String type;
    /** The number for matching: upper case, letters and digits only. */
    public String numberKey;
    /** The number as it was read. */
    public String number;
    /** ISO 3166-1 alpha-2; null only when nothing said it — neither the document nor the customer's nationality. */
    public String issuingCountry;
    public LocalDate expiry;
    /** One of {@link Origin}: where it was seen first. */
    public String origin;
    public Instant firstSeenAt;
    public Instant lastSeenAt;

    /**
     * The type as the MDM keeps it. Scanners and channels say it in their own words; what is not one of
     * the known types is OTHER — the number, not the type, is what tells documents apart.
     */
    public static String type(String type) {
        if (type == null || type.isBlank()) {
            return Type.OTHER.name();
        }
        var t = type.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return switch (t) {
            case "DNI", "NIF" -> Type.DNI.name();
            case "PASSPORT", "PASAPORTE", "P" -> Type.PASSPORT.name();
            case "ID_CARD", "IDCARD", "ID", "I" -> Type.ID_CARD.name();
            case "RESIDENCE", "NIE", "RESIDENCE_PERMIT" -> Type.RESIDENCE.name();
            default -> Type.OTHER.name();
        };
    }

    /** An ISO-2 country as the MDM keeps it; null when there is none. */
    public static String country(String country) {
        return country == null || country.isBlank() ? null : country.trim().toUpperCase(Locale.ROOT);
    }
}
