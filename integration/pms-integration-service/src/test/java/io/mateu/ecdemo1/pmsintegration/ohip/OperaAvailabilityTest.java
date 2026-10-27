package io.mateu.ecdemo1.pmsintegration.ohip;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.pmsintegration.connections.Connections;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Opera's free rooms, read through the only search the tenant has: «would you sell N of each type for
 * the stay?». A count is that search asked for more rooms until the type drops out, capped.
 */
class OperaAvailabilityTest {

    static final ObjectMapper JSON = new ObjectMapper();
    /** XMAR as a night of November reads: no DBPB left, two SJLB, plenty of STDK. */
    static final Map<String, Integer> FREE = Map.of("DBPB", 0, "SJLB", 2, "STDK", 7);

    final OhipClient ohip = mock(OhipClient.class);
    final AtomicInteger searches = new AtomicInteger();
    final OperaAvailability availability = new OperaAvailability(ohip, mock(Connections.class),
            Clock.fixed(Instant.parse("2026-11-15T09:00:00Z"), ZoneOffset.UTC));

    OperaAvailabilityTest() {
        when(ohip.get(anyString(), anyString(), any(Object[].class))).thenAnswer(call -> {
            searches.incrementAndGet();
            int quantity = (Integer) call.getArgument(5);
            var rates = FREE.entrySet().stream().filter(e -> e.getValue() >= quantity)
                    .map(e -> "{\"roomType\": \"" + e.getKey() + "\", \"ratePlanCode\": \"BAR\", \"numberOfUnits\": " + quantity + "}")
                    .collect(Collectors.joining(","));
            return new OhipClient.Response(JSON.readTree("{\"hotelAvailability\": [{\"roomStays\": [{\"roomRates\": [" + rates + "]}]}]}"), null);
        });
    }

    @Test
    void whatOperaWouldSellForAStay() {
        var nov16 = LocalDate.of(2026, 11, 16);
        assertThat(availability.sellable("XMAR", nov16, nov16.plusDays(3), 1)).containsExactly("SJLB", "STDK");
        assertThat(availability.sellable("XMAR", nov16, nov16.plusDays(3), 3)).containsExactly("STDK");
    }

    @Test
    void roomsFitWhenOperaWouldSellAsManyOfEachType() {
        var nov16 = LocalDate.of(2026, 11, 16);
        assertThat(availability.fits("XMAR", nov16, nov16.plusDays(3), List.of("SJLB", "SJLB"))).isTrue();
        assertThat(availability.fits("XMAR", nov16, nov16.plusDays(3), List.of("SJLB", "SJLB", "SJLB"))).isFalse();
        assertThat(availability.fits("XMAR", nov16, nov16.plusDays(3), List.of("DBPB"))).isFalse();
        assertThat(availability.fits("XMAR", nov16, nov16.plusDays(3), List.of("STDK", "SJLB"))).isTrue();
    }

    @Test
    void theCalendarCountsEachTypeUpToTheCapAndIsReadOnceInAWhile() {
        var calendar = availability.calendar("XMAR", 2);
        assertThat(calendar.from()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(calendar.cap()).isEqualTo(5);
        assertThat(calendar.nights()).hasSize(2).allSatisfy(n -> assertThat(n.free())
                .containsEntry("SJLB", 2).containsEntry("STDK", 5).doesNotContainKey("DBPB"));
        var read = searches.get();
        assertThat(availability.calendar("XMAR", 2)).isSameAs(calendar);
        assertThat(searches.get()).as("read once in a while, not on every page").isEqualTo(read);
    }
}
