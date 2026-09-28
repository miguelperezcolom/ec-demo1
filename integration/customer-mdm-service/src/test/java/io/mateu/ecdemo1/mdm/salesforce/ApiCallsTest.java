package io.mateu.ecdemo1.mdm.salesforce;

import io.mateu.ecdemo1.integration.model.usage.ApiCalls;
import io.mateu.ecdemo1.mdm.config.MdmConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The MDM's calls to Salesforce, counted by purpose over the rolling 24 hours the org counts in. */
class ApiCallsTest {

    final SalesforceBudgetTest.Hands clock = new SalesforceBudgetTest.Hands();
    final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    final ApiCalls calls = new MdmConfig().salesforceCalls(clock, registry);

    @Test
    void callsAreCountedByPurposeTheBusiestFirst() {
        calls.record("refresh", ApiCalls.Outcome.OK);
        calls.record("poll", ApiCalls.Outcome.OK);
        calls.record("poll", ApiCalls.Outcome.ERROR);
        calls.record("poll", ApiCalls.Outcome.LIMITED);

        assertThat(calls.byPurpose()).containsExactly(java.util.Map.entry("poll", 3L), java.util.Map.entry("refresh", 1L));
        assertThat(calls.total()).isEqualTo(4);
        assertThat(calls.total(ApiCalls.Outcome.LIMITED)).isEqualTo(1);
        assertThat(calls.total(ApiCalls.Outcome.ERROR)).isEqualTo(1);
    }

    @Test
    void anHourLeavesTheCountWhenItLeavesTheRolling24Hours() {
        calls.record("poll", ApiCalls.Outcome.OK);
        clock.advance(Duration.ofHours(1));
        calls.record("poll", ApiCalls.Outcome.OK);
        clock.advance(Duration.ofHours(23));
        // The first call's hour is now 24 hours ago: out. The second's is 23 hours ago: in.
        assertThat(calls.total()).isEqualTo(1);
        assertThat(calls.live()).hasSize(1);
    }

    @Test
    void theLastHourAgainstTheHourBeforeSaysWhetherWeAreSpeedingUp() {
        calls.record("poll", ApiCalls.Outcome.OK);
        clock.advance(Duration.ofMinutes(70));
        calls.record("poll", ApiCalls.Outcome.OK);
        calls.record("refresh", ApiCalls.Outcome.OK);
        calls.record(SalesforceClient.Purpose.TOKEN, ApiCalls.Outcome.OK);
        assertThat(calls.lastHour(SalesforceClient.Purpose.TOKEN)).isEqualTo(2);
        assertThat(calls.previousHour(SalesforceClient.Purpose.TOKEN)).isEqualTo(1);
        clock.advance(Duration.ofHours(2));
        assertThat(calls.lastHour()).isZero();
        assertThat(calls.previousHour()).isZero();
    }

    @Test
    void tokensCanBeLeftOutOfTheTotalTheOrgCounts() {
        calls.record(SalesforceClient.Purpose.TOKEN, ApiCalls.Outcome.OK);
        calls.record("projection", ApiCalls.Outcome.OK);
        assertThat(calls.total(SalesforceClient.Purpose.TOKEN)).isEqualTo(1);
    }

    @Test
    void whatAnEarlierRunCountedIsPutBackWithoutCountingTwice() {
        calls.record("poll", ApiCalls.Outcome.OK);
        var hour = calls.live().getFirst().hour();
        calls.restore(List.of(new ApiCalls.Bucket(hour, "poll", ApiCalls.Outcome.OK, 5),
                new ApiCalls.Bucket(hour.minus(Duration.ofHours(2)), "merge", ApiCalls.Outcome.OK, 2),
                new ApiCalls.Bucket(Instant.parse("2026-09-20T00:00:00Z"), "merge", ApiCalls.Outcome.OK, 99)));
        assertThat(calls.byPurpose()).containsExactly(java.util.Map.entry("poll", 5L), java.util.Map.entry("merge", 2L));
    }

    @Test
    void everyCallIsAlsoAMicrometerCounterByPurposeAndOutcome() {
        calls.record("poll", ApiCalls.Outcome.OK);
        calls.record("poll", ApiCalls.Outcome.OK);
        calls.record("poll", ApiCalls.Outcome.LIMITED);
        assertThat(registry.get("salesforce.api.calls").tags("purpose", "poll", "outcome", "ok").counter().count()).isEqualTo(2);
        assertThat(registry.get("salesforce.api.calls").tags("purpose", "poll", "outcome", "limited").counter().count()).isEqualTo(1);
    }
}
