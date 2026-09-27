package io.mateu.ecdemo1.crsintegration.rest;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The desk's walk-in as the CRS's booking: channel WALKIN, the front office's reference, the holder its first guest. */
@SuppressWarnings("unchecked")
class WalkInControllerTest {

    static WalkInController.WalkIn walkIn() {
        return new WalkInController.WalkIn("MRU01", "FO-7K2Q9M", LocalDate.of(2026, 9, 27), LocalDate.of(2026, 9, 29),
                "STD-KING", "DIRECTA", "DESAYUNO", 2, null,
                new WalkInController.Holder("Nora", "Vega", "nora@example.com", "+34 600", "ES", "PASSPORT", "X123"),
                new BigDecimal("420.00"));
    }

    @Test
    void theBookingCarriesTheChannelTheReferenceAndTheHolderAsItsFirstGuest() {
        var booking = WalkInController.booking(walkIn(), true);

        assertThat(booking).containsEntry("channelCode", "WALKIN").containsEntry("externalReference", "FO-7K2Q9M");
        assertThat((Map<String, Object>) booking.get("holder")).containsEntry("firstName", "Nora").containsEntry("email", "nora@example.com");
        var room = (Map<String, Object>) ((List<?>) booking.get("rooms")).getFirst();
        assertThat(room).containsEntry("roomTypeCode", "STD-KING").containsEntry("adults", 2).containsEntry("childrenAges", List.of());
        assertThat((List<?>) room.get("guests")).singleElement()
                .satisfies(g -> assertThat((Map<String, Object>) g).containsEntry("documentNumber", "X123").containsEntry("type", "Adult"));
    }

    @Test
    void aQuoteSendsNobody() {
        var booking = WalkInController.booking(walkIn(), false);

        assertThat(booking).doesNotContainKey("holder");
        var room = (Map<String, Object>) ((List<?>) booking.get("rooms")).getFirst();
        assertThat((List<?>) room.get("guests")).isEmpty();
    }
}
