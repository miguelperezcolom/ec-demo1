package io.mateu.ecdemo1.booking.domain;

import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog;
import io.mateu.ecdemo1.booking.domain.catalog.CrsCatalog.RatePlan;
import io.mateu.ecdemo1.booking.infra.out.catalog.ImportedCatalogs;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A rate plan opened while the CRS runs: in the hotel's own codes, or the chain's. */
class RatePlanAdditionTest {

    final CrsCatalog catalog = ImportedCatalogs.standardCatalog();

    @Test
    void aHotelWithCodesOfItsOwnGetsItAmongThemAndTheChainDoesNot() {
        var plan = RatePlan.valid("STAFF-27", "Empleados 2027", new BigDecimal("0.5"));

        assertThat(catalog.addRatePlan("MRU01", plan)).isTrue();

        assertThat(catalog.ratePlan("MRU01", "STAFF-27")).isEqualTo(plan);
        assertThat(catalog.codes("PMI01").ratePlans()).extracting(RatePlan::code).doesNotContain("STAFF-27");
    }

    @Test
    void aHotelThatSellsWithTheChainsCodesGetsItForTheChain() {
        catalog.addRatePlan("PMI01", RatePlan.valid("SPRING", "Primavera", new BigDecimal("0.9")));

        assertThat(catalog.ratePlan("CUN01", "SPRING").name()).isEqualTo("Primavera");
        assertThat(catalog.codes("MRU01").ratePlans()).extracting(RatePlan::code).doesNotContain("SPRING");
    }

    @Test
    void theSamePlanAgainChangesNothingAndAnotherByTheSameCodeIsRefused() {
        var plan = RatePlan.valid("STAFF-27", "Empleados 2027", new BigDecimal("0.50"));
        catalog.addRatePlan("MRU01", plan);

        assertThat(catalog.addRatePlan("MRU01", RatePlan.valid("STAFF-27", "Empleados 2027", new BigDecimal("0.5")))).isFalse();
        assertThat(catalog.codes("MRU01").ratePlans()).filteredOn(p -> p.code().equals("STAFF-27")).hasSize(1);
        assertThatThrownBy(() -> catalog.addRatePlan("MRU01", RatePlan.valid("DIRECTA", "Otra", BigDecimal.ONE)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Venta directa");
    }

    @Test
    void aCodeTheChannelsCannotSendOrAFactorOutOfRangeIsRefused() {
        assertThatThrownBy(() -> RatePlan.valid("staff 27", "x", BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RatePlan.valid("STAFF", " ", BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RatePlan.valid("STAFF", "x", new BigDecimal("0"))).isInstanceOf(IllegalArgumentException.class);
    }
}
