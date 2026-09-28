package io.mateu.ecdemo1.integration.model.pms;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;

/**
 * The PMS connector wrote a reservation into the PMS — created, changed, cancelled — or found it
 * already there: whoever consumes the PMS reads it again from the PMS. Published on the
 * {@code pms-reservations} topic. It says what, not how: the consumers — the pms-fo integration, which
 * projects it to the hotel's front office — read the reservation as the PMS holds it.
 *
 * @param origin what wrote it: {@code proyectar-reserva}, {@code proyectar-cancelacion}…
 */
public record PmsReservationChanged(String eventId, String pmsHotelCode, String pmsReservationId, String crsHotelCode,
                                    String locator, String origin, Instant occurredAt) {

    public static final String TOPIC = "pms-reservations";

    /** The Kafka key: the changes of one reservation stay in order. */
    @JsonIgnore
    public String key() {
        return pmsHotelCode + "/" + pmsReservationId;
    }
}
