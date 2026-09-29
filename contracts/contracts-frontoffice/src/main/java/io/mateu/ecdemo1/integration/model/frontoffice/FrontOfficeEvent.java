package io.mateu.ecdemo1.integration.model.frontoffice;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What happened at a hotel's reception, as the front office says it: a guest checked in, checked out,
 * or never came; the desk charged something to the stay's folio, or took a charge back. The PMS is the master of the stay, so what the desk did goes UP to it: the pms-fo
 * integration takes these from the {@code front-office-events} topic and starts the process that
 * records it in the PMS («registrar-checkin», «registrar-checkout», «registrar-no-show-pms»). Written
 * to the front office's outbox in the transaction of the desk's decision; the consumer deduplicates on
 * {@link #eventId()}, and the engine on the process key, so taking one twice records it once.
 *
 * <p>Every event names the stay both ways: the front office's own id and hotel (the CRS's code), the
 * CRS's locator when the reservation came from the CRS (null for one born in the PMS, or a walk-in the
 * CRS has not booked yet), and the PMS reservation when the front office has it linked (null for a
 * walk-in that has not reached the PMS yet: the PMS side finds it by the CRS locator, or waits for it).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = FrontOfficeEvent.GuestCheckedIn.class, name = "guest-checked-in"),
        @JsonSubTypes.Type(value = FrontOfficeEvent.GuestCheckedOut.class, name = "guest-checked-out"),
        @JsonSubTypes.Type(value = FrontOfficeEvent.NoShowReported.class, name = "no-show-reported"),
        @JsonSubTypes.Type(value = FrontOfficeEvent.ChargePosted.class, name = "charge-posted"),
        @JsonSubTypes.Type(value = FrontOfficeEvent.ChargeVoided.class, name = "charge-voided"),
})
public sealed interface FrontOfficeEvent {

    /** The topic the front office publishes them on. */
    String TOPIC = "front-office-events";

    /** Unique per event; the consumer deduplicates on it. */
    String eventId();

    /** The front office's hotel — the CRS's code of it (MRU01). */
    String hotelCode();

    /** The front office's stay. */
    String stayId();

    /** The CRS's locator, when the reservation came from the CRS. */
    String crsLocator();

    /** The PMS property the front office consumes. */
    String pmsHotelCode();

    /** The PMS reservation, when the front office has the stay linked to it. */
    String pmsReservationId();

    /** The Kafka key: what is said about one stay stays in order. */
    @JsonIgnore
    default String key() {
        return hotelCode() + "/" + stayId();
    }

    /**
     * The desk checked the guests in.
     *
     * @param roomNumber the room the desk gave them; null if the desk did not choose one (the PMS's
     *                   assignment stands, or the PMS suggests one)
     * @param pax        how many people arrived
     */
    record GuestCheckedIn(String eventId, Instant at, String hotelCode, String stayId, String crsLocator,
                          String pmsHotelCode, String pmsReservationId, String roomNumber, int pax, String by)
            implements FrontOfficeEvent {
    }

    /** The desk checked the guests out: the room is free, the folio closed at the desk. */
    record GuestCheckedOut(String eventId, Instant at, String hotelCode, String stayId, String crsLocator,
                           String pmsHotelCode, String pmsReservationId, String roomNumber, String by)
            implements FrontOfficeEvent {
    }

    /**
     * Nobody of the reservation arrived: the desk marked every guest as a no-show. The PMS records it,
     * and reports it to the CRS, which applies its fee.
     */
    record NoShowReported(String eventId, Instant at, String hotelCode, String stayId, String crsLocator,
                          String pmsHotelCode, String pmsReservationId, int pax, String by)
            implements FrontOfficeEvent {
    }

    /** What a charge the desk puts on a stay's folio is: the PMS books each kind with its own transaction code. */
    enum ChargeKind {
        /** An extra contracted at the check-in (a transfer, a dinner…): its code is the add-on's. */
        ADD_ON,
        /** The late check-out. */
        LATE_CHECK_OUT,
        /** A consumption posted during the stay from the desk's charge catalogue (minibar, room service…): its code is the catalogue's. */
        CONSUMPTION
    }

    /**
     * The desk charged something to the stay's folio — a late check-out, an extra, a consumption. The PMS
     * is the master of the folio: the pms-fo integration posts it to the reservation's folio in the PMS
     * («registrar-cargo»), once per folio line: {@code lineId} is the idempotency key, the PMS keeps it
     * as the posting's reference. The stay's accommodation is not one: the PMS charges it itself.
     *
     * @param lineId      the front office's folio line: unique, and what a void names
     * @param kind        what the charge is
     * @param code        the front office's code of it — the catalogue's, the add-on's; null for a late check-out
     * @param description the line's concept, as the folio shows it
     * @param amount      what it costs, in the property's currency
     * @param currency    ISO 4217; null for the PMS property's own
     */
    record ChargePosted(String eventId, Instant at, String hotelCode, String stayId, String crsLocator,
                        String pmsHotelCode, String pmsReservationId, String lineId, ChargeKind kind, String code,
                        String description, BigDecimal amount, String currency, String by)
            implements FrontOfficeEvent {
    }

    /**
     * The desk took back a charge of the stay's folio — voided, or refunded: the line no longer counts.
     * The PMS reverses its posting («anular-cargo»): the same amount, negative, with the same
     * transaction code. It names the line as its {@link ChargePosted} did, with the same figures.
     */
    record ChargeVoided(String eventId, Instant at, String hotelCode, String stayId, String crsLocator,
                        String pmsHotelCode, String pmsReservationId, String lineId, ChargeKind kind, String code,
                        String description, BigDecimal amount, String currency, String by)
            implements FrontOfficeEvent {
    }
}
