package io.mateu.ecdemo1.booking.domain;

import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.NightlyRate;
import io.mateu.ecdemo1.booking.domain.aggregates.booking.vo.Stay;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.ecdemo1.booking.domain.services.RoomPricing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomPricingTest {

    final CrsCatalog catalog = CrsCatalog.standard();
    final RoomPricing pricing = new RoomPricing();

    @Test
    void pricesEveryNightWithBoardPerPersonAndWeekendSurcharge() {
        // Thursday, Friday and Saturday nights: only the last two are weekend nights.
        var thursday = Fixtures.MONDAY.plusDays(3);
        var rates = pricing.price(catalog.roomType("PMI01", "JSU"), catalog.ratePlan("NRF"), catalog.board("AD"),
                2, List.of(8, 1), new Stay(thursday, thursday.plusDays(3)));

        // Room: 210 × 0.90 = 189, × 1.15 on weekends = 217.35.
        // Board: 15 × 2 adults + 7.50 for the 8-year-old; the 1-year-old is free = 37.50.
        assertThat(rates).map(NightlyRate::amount).map(a -> a.toPlainString())
                .containsExactly("226.50", "254.85", "254.85");
        assertThat(rates).map(NightlyRate::date)
                .containsExactly(thursday, thursday.plusDays(1), thursday.plusDays(2));
    }

    @Test
    void refusesMorePeopleThanTheRoomHolds() {
        assertThatThrownBy(() -> pricing.price(catalog.roomType("PMI01", "IND"), catalog.ratePlan("BAR"),
                catalog.board("SA"), 2, List.of(), new Stay(Fixtures.MONDAY, Fixtures.MONDAY.plusDays(1))))
                .hasMessageContaining("at most 1");
    }

    @Test
    void anUnknownCodeNamesTheValidOnes() {
        assertThatThrownBy(() -> catalog.roomType("PMI01", "SUI"))
                .hasMessageContaining("IND, DBL, DBLSV, JSU");
    }
}
