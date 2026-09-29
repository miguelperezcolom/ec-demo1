package io.mateu.ecdemo1.integration.model.frontoffice;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What the pms-fo integration tells a hotel's front office: the stays as the PMS holds them, and the
 * PMS's catalogue the front office reads them with. Sent on the {@code front-office-commands} topic and
 * taken once — the front office deduplicates on {@link #commandId()}, and a stay is written by state,
 * never by increments, so taking the same one twice leaves the front office as taking it once.
 *
 * <p>The front office consumes the PMS (the chain CRS → PMS → front office): nothing here comes from
 * the CRS. Codes are the PMS's own; the front office turns them into words with the catalogue the
 * same integration gave it ({@link ReplaceCatalogue}).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = FrontOfficeCommand.WriteStay.class, name = "write-stay"),
        @JsonSubTypes.Type(value = FrontOfficeCommand.ReplaceCatalogue.class, name = "replace-catalogue"),
        @JsonSubTypes.Type(value = FrontOfficeCommand.RecordReception.class, name = "record-reception"),
})
public sealed interface FrontOfficeCommand {

    /** The topic the front office takes these from. */
    String TOPIC = "front-office-commands";

    /** Unique per command; the front office deduplicates on it. */
    String commandId();

    /** The Kafka key: what is said about one reservation, or one catalogue, stays in order. */
    @JsonIgnore
    String key();

    /** A person of a stay as the PMS has it: its customer code in the chain's MDM, when the MDM knows it. */
    record Person(String customerId, String pmsProfileId, String name, String document, String email, String phone) {
    }

    /** Where a PMS reservation stands, for the front office. */
    enum PmsStatus {
        /** Expected, or anything short of cancelled: the stay's data is the reservation's. */
        RESERVED,
        /** Cancelled in the PMS: a stay still to arrive is cancelled; one in the house is the desk's. */
        CANCELLED,
        /** Cancelled as a no-show: the stay costs what the PMS still charges ({@code total}). */
        NO_SHOW,
        /** Checked in in the PMS: the guests are in the house. */
        IN_HOUSE,
        /** Checked out in the PMS: the guests left, the PMS closed the folio. */
        CHECKED_OUT
    }

    /**
     * A reservation as the PMS holds it now — created, changed or cancelled. Ordered by
     * {@code pmsVersion}: a front office that already holds a later version of the reservation keeps
     * it; the same version again is written again (harmless), so a change to the guest that did not
     * touch the reservation — a merge in the MDM — still reaches the stay.
     *
     * @param pmsHotelCode       the PMS property
     * @param pmsReservationId   the PMS's own id of the reservation: what the front office knows the stay by
     * @param confirmationNumber the PMS's confirmation number, for the desk
     * @param crsLocator         the CRS's locator, when the reservation came from the CRS (its external
     *                           reference in the integration's context); null for one born in the PMS
     * @param externalReferences every other reference the PMS keeps on it (a channel's voucher, a walk-in's
     *                           stay id): how the front office recognises a stay it already has
     * @param pmsVersion         the PMS's last modification, ISO local date-time
     *                           ({@code 2026-09-27T21:57:59}): the order guard
     * @param roomTypeCode       the PMS's room type
     * @param ratePlanCode       the PMS's rate plan
     * @param boardCode          the PMS's package that is the board; null for room only
     * @param agency             who sold it, in words: the travel agent or company on the reservation, or
     *                           the PMS's source
     * @param total              what the stay costs as the PMS rates it (for a no-show: its fee)
     */
    record WriteStay(String commandId, String pmsHotelCode, String pmsReservationId, String confirmationNumber,
                     String crsLocator, List<String> externalReferences, String pmsVersion, PmsStatus status,
                     Person holder, List<Person> companions, String roomTypeCode, String ratePlanCode,
                     String boardCode, LocalDate checkIn, LocalDate checkOut, int pax, String agency,
                     BigDecimal total, String currency) implements FrontOfficeCommand {
        @Override
        public String key() {
            return pmsHotelCode + "/" + pmsReservationId;
        }
    }

    /** What the reception did that the PMS was asked to record (a {@link FrontOfficeEvent}). */
    enum ReceptionOperation {
        CHECK_IN, CHECK_OUT, NO_SHOW
    }

    /**
     * The invoice the PMS produced at the check-out — the PMS is the master of the folio.
     *
     * @param source   who produced it: {@code OPERA} for the PMS's own document
     * @param number   the PMS's folio number, as the PMS prints it ({@code XMAR377})
     * @param date     the folio's date (the PMS's business date)
     * @param amount   what the folio adds up to
     * @param pdf      the printable document, base 64 — null when the PMS gave the figures but no document
     */
    record Invoice(String source, String number, LocalDate date, BigDecimal amount, String currency, String pdf) {
    }

    /**
     * How the PMS took what the reception did — told to the front office by the process that recorded
     * it. The state it brought about comes back by {@link WriteStay} (in the house, checked out); this
     * says what that one cannot: the PMS refused, and why; the room the PMS put the guests in; the
     * invoice of the check-out.
     *
     * @param stayId     the front office's stay the operation was about
     * @param refused    whether the PMS refused it: {@code detail} says why, and the process waits on a
     *                   cause someone resolves
     * @param detail     the PMS's words, when refused; otherwise what was done
     * @param roomNumber the room the PMS has the guests in, after a check-in
     * @param invoice    the PMS's invoice, after a check-out; null otherwise, or when the PMS gave none
     */
    record RecordReception(String commandId, String pmsHotelCode, String pmsReservationId, String stayId,
                           ReceptionOperation operation, boolean refused, String detail, String roomNumber,
                           Invoice invoice) implements FrontOfficeCommand {
        @Override
        public String key() {
            return pmsHotelCode + "/" + pmsReservationId;
        }
    }

    /** What a code of the PMS is, in the PMS's words. For a room, {@code extra} is its room type. */
    record CatalogueEntry(CatalogueType type, String code, String description, String extra) {
    }

    enum CatalogueType {
        ROOM_TYPE, RATE_PLAN, PACKAGE, ROOM
    }

    /**
     * The PMS's catalogue of the property, whole: what the front office reads the stays with. Replaces
     * the one it had for the property.
     */
    record ReplaceCatalogue(String commandId, String pmsHotelCode, List<CatalogueEntry> entries)
            implements FrontOfficeCommand {
        @Override
        public String key() {
            return pmsHotelCode;
        }
    }
}
