package io.mateu.ecdemo1.pmsintegration;

import io.mateu.ecdemo1.integration.model.reservation.Reservation;
import io.mateu.ecdemo1.pmsintegration.clients.IntegrationClients;

/** ReservationPayloadTest's fixtures, for tests in other packages. */
public final class ReservationPayloadFixtures {

    private ReservationPayloadFixtures() {
    }

    public static Reservation reservation() {
        return ReservationPayloadTest.reservation(java.util.List.of(), "AD");
    }

    public static IntegrationClients.Resolved codes() {
        return ReservationPayloadTest.codes();
    }
}
