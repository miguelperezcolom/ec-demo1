package io.mateu.ecdemo1.integration.model.command;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Asks the CRS adapter to project a reservation nobody changed, by the same path a change in the CRS
 * takes («Proyectar Reserva») — the backfill's way in. Sent through the integrations service's outbox
 * on the {@code projection-requests} topic. Taken twice, it starts the process once: its key comes
 * from the reservation and the origin.
 *
 * @param origin who asked, e.g. {@code backfill:<run>}: part of the process key, and how the
 *               preparation knows not to hold it for the activation
 */
public record ProjectReservation(String commandId, String hotelCode, String locator, String origin) {

    /** The Kafka key: the requests about one reservation stay in order. */
    @JsonIgnore
    public String key() {
        return hotelCode + "/" + locator;
    }
}
