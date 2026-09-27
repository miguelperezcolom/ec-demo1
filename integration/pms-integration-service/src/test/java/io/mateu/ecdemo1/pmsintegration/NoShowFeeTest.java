package io.mateu.ecdemo1.pmsintegration;

import io.mateu.ecdemo1.integration.model.reservation.NightlyRate;
import io.mateu.ecdemo1.integration.model.reservation.Reservation;
import io.mateu.ecdemo1.integration.model.reservation.ReservationStatus;
import io.mateu.ecdemo1.integration.model.reservation.Room;
import io.mateu.ecdemo1.pmsintegration.worker.TaskHandlers;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A no-show written to Opera costs its fee: every night by the same share, adding up to the fee exactly. */
class NoShowFeeTest {

    static Reservation noShow(BigDecimal fee, BigDecimal original, BigDecimal... nights) {
        var day = LocalDate.of(2026, 11, 3);
        var rates = new java.util.ArrayList<NightlyRate>();
        for (int i = 0; i < nights.length; i++) rates.add(new NightlyRate(day.plusDays(i), nights[i]));
        return new Reservation("MRU01", "L1", 3, ReservationStatus.CANCELLED, "WEB", null, null, day, day.plusDays(nights.length),
                "EUR", null, List.of(new Room(1, "JSU", "BAR", "SA", 2, List.of(), List.of(), rates)), List.of(), fee, null,
                "NOS", fee, original);
    }

    @Test
    void theNightsAddUpToTheFee() {
        var r = TaskHandlers.withFee(noShow(new BigDecimal("120.00"), new BigDecimal("480.00"),
                new BigDecimal("240.00"), new BigDecimal("240.00")));
        assertThat(r.rooms().get(0).nightlyRates()).extracting(NightlyRate::amount)
                .containsExactly(new BigDecimal("60.00"), new BigDecimal("60.00"));
        assertThat(r.totalAmount()).isEqualByComparingTo("120.00");
    }

    @Test
    void theLastNightTakesTheRounding() {
        var r = TaskHandlers.withFee(noShow(new BigDecimal("33.34"), new BigDecimal("133.35"),
                new BigDecimal("44.45"), new BigDecimal("44.45"), new BigDecimal("44.45")));
        var nights = r.rooms().get(0).nightlyRates().stream().map(NightlyRate::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(nights).isEqualByComparingTo("33.34");
    }
}
