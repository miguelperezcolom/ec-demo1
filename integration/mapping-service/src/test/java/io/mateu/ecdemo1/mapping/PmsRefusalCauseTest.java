package io.mateu.ecdemo1.mapping;

import io.mateu.ecdemo1.integration.model.mapping.Cause;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opera refusing several reservations alike — no rooms of the type left, RSV00138 — is one incident
 * with them behind it, keyed by the hotel, the step and Opera's code; a refusal with no code stays the
 * reservation's own.
 */
class PmsRefusalCauseTest {

    @Test
    void refusalsWithTheSameOperaCodeShareOneCause() {
        var a = Cause.pmsRejectedReservation("MRU01", "6BNAY7", "upsert-reservation",
                "There are not enough rooms available on Room Type level — RSV00138");
        var b = Cause.pmsRejectedReservation("MRU01", "3NMNYW", "upsert-reservation",
                "There are not enough rooms available on Room Type level (RSV00138) — RSV00138");
        assertThat(a.key()).isEqualTo("PMS_REJECTED:MRU01:upsert-reservation:RSV00138").isEqualTo(b.key());
        assertThat(a.description()).isEqualTo("The PMS refused upsert-reservation of reservations of hotel MRU01: "
                + "There are not enough rooms available on Room Type level — RSV00138");
        assertThat(a.key()).startsWith(Cause.pmsRejectedStepPrefix("MRU01", "upsert-reservation"));
    }

    @Test
    void anotherStepHotelOrCodeIsAnotherCause() {
        var rooms = Cause.pmsRejectedReservation("MRU01", "L1", "upsert-reservation", "No rooms — RSV00138").key();
        assertThat(Cause.pmsRejectedReservation("PMI01", "L1", "upsert-reservation", "No rooms — RSV00138").key()).isNotEqualTo(rooms);
        assertThat(Cause.pmsRejectedReservation("MRU01", "L2", "upsert-reservation", "No rooms — RSV00138").key()).isEqualTo(rooms);
        assertThat(Cause.pmsRejectedReservation("MRU01", "L1", "upsert-reservation", "Rate closed — RSV00201").key()).isNotEqualTo(rooms);
    }

    @Test
    void theDesksAndTheFoliosRefusalsStayEachReservationsOwn() {
        // a check-in refused because the room is not ready is fixed on that stay: another room
        assertThat(Cause.pmsRejectedReservation("MRU01", "GSX4AK", "check-in-reservation", "Room is not clean — FOF00067").key())
                .isEqualTo("PMS_REJECTED:MRU01:GSX4AK:check-in-reservation");
        assertThat(Cause.pmsRejectedReservation("MRU01", "GSX4AK", "post-charge", "Not in house — FOF00102").key())
                .isEqualTo("PMS_REJECTED:MRU01:GSX4AK:post-charge");
    }

    @Test
    void aRefusalWithNoCodeIsTheReservationsOwn() {
        var cause = Cause.pmsRejectedReservation("MRU01", "GSX4AK", "post-charge", "Opera answered 400");
        assertThat(cause.key()).isEqualTo("PMS_REJECTED:MRU01:GSX4AK:post-charge")
                .isEqualTo(Cause.pmsRejectedReservationKey("MRU01", "GSX4AK", "post-charge"));
        assertThat(cause.description()).isEqualTo("The PMS refused post-charge of reservation GSX4AK of hotel MRU01: Opera answered 400");
    }
}
