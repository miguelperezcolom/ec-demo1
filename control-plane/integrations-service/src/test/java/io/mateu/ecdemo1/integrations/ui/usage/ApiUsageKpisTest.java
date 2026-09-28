package io.mateu.ecdemo1.integrations.ui.usage;

import io.mateu.ecdemo1.integration.model.usage.ApiUsage;
import io.mateu.uidl.data.MetricTrend;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The home pages' tiles: the calls left first, which way they go, and our pace. */
class ApiUsageKpisTest {

    static ApiUsage salesforce(long used, Long usedAnHourAgo, long ours1h, long oursBefore, boolean paused) {
        return new ApiUsage("salesforce", "customer-mdm", used, 15000L, Instant.parse("2026-09-28T10:26:00Z"), 400,
                Map.of(), null, used - 400, 0, 0, paused, paused ? Instant.parse("2026-09-28T11:05:00Z") : null, null,
                null, ours1h, oursBefore, usedAnHourAgo);
    }

    @Test
    void theMainFigureIsTheCallsLeftAndWhetherTheyAreGoingDown() {
        var card = ApiUsageKpis.salesforce(salesforce(14665, 14500L, 42, 30, false));
        assertThat(card.value()).isEqualTo("335");
        assertThat(card.unit()).isEqualTo("de 15.000");
        assertThat(card.trend()).isEqualTo(MetricTrend.down);
        assertThat(card.trendLabel()).isEqualTo("-165 en la última hora");
        assertThat(card.description()).startsWith("visto 28/09 12:26");
    }

    @Test
    void callsRollingOutOfTheWindowAreTheAllowanceComingBack() {
        var card = ApiUsageKpis.salesforce(salesforce(14000, 14665L, 0, 0, false));
        assertThat(card.trend()).isEqualTo(MetricTrend.up);
        assertThat(card.trendLabel()).isEqualTo("+665 en la última hora");
    }

    @Test
    void aPauseIsSaidFirst() {
        assertThat(ApiUsageKpis.salesforce(salesforce(15000, null, 0, 0, true)).description())
                .startsWith("EN PAUSA hasta las 28/09 13:05");
    }

    @Test
    void ourPaceAgainstTheHourBefore() {
        var card = ApiUsageKpis.salesforcePace(salesforce(14665, null, 42, 30, false));
        assertThat(card.value()).isEqualTo("42");
        assertThat(card.trend()).isEqualTo(MetricTrend.neutral);
        assertThat(card.trendLabel()).isEqualTo("↑ frente a 30 la hora anterior");
    }

    @Test
    void nothingToSayIsADashNotAZero() {
        assertThat(ApiUsageKpis.salesforce(null).value()).isEqualTo("—");
        assertThat(ApiUsageKpis.opera(null).value()).isEqualTo("—");
        var opera = new ApiUsage("opera", "pms-integration", null, null, null, 1234, Map.of(), null, null, 2, 0, false, null,
                null, 97L, 12, 20, null);
        assertThat(ApiUsageKpis.opera(opera).value()).isEqualTo("1.234");
        assertThat(ApiUsageKpis.opera(opera).description()).isEqualTo("2 × 429 · 97 libres según OHIP");
    }
}
