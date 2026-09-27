package io.mateu.ecdemo1.mdm.salesforce;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SalesforceBudgetTest {

    /** A clock the test moves. */
    static class Hands extends Clock {
        Instant now = Instant.parse("2026-09-27T21:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return now;
        }
    }

    final Hands clock = new Hands();
    final SalesforceBudget budget = new SalesforceBudget(clock, Duration.ofMinutes(5), Duration.ofMinutes(60));
    final List<String> told = new ArrayList<>();

    {
        budget.listener(new SalesforceBudget.Listener() {
            public void paused(SalesforceBudget.Pause pause) {
                told.add("paused until " + pause.until());
            }

            public void resumed(SalesforceBudget.Pause pause) {
                told.add("resumed");
            }
        });
    }

    @Test
    void aRefusalPausesCallsLongerEachTimeUpToTheLongestAndIsToldOnce() {
        assertThat(budget.open()).isTrue();

        budget.exceeded("REQUEST_LIMIT_EXCEEDED");
        assertThat(budget.open()).isFalse();
        assertThat(budget.pausedUntil()).isEqualTo(Instant.parse("2026-09-27T21:05:00Z"));

        // A call already in flight refused too: the same pause, not a longer one.
        budget.exceeded("REQUEST_LIMIT_EXCEEDED");
        assertThat(budget.pausedUntil()).isEqualTo(Instant.parse("2026-09-27T21:05:00Z"));

        // The probe after each pause refused again: 10, 20, 40 minutes, then never more than 60.
        var waits = new ArrayList<Duration>();
        for (int i = 0; i < 5; i++) {
            clock.now = budget.pausedUntil();
            assertThat(budget.open()).isTrue();
            budget.exceeded("REQUEST_LIMIT_EXCEEDED");
            waits.add(Duration.between(clock.now, budget.pausedUntil()));
        }
        assertThat(waits).containsExactly(Duration.ofMinutes(10), Duration.ofMinutes(20), Duration.ofMinutes(40),
                Duration.ofMinutes(60), Duration.ofMinutes(60));
        assertThat(told).containsExactly("paused until 2026-09-27T21:05:00Z");

        // A call goes through: the episode ends, said once, and the next one starts from the first pause.
        clock.now = budget.pausedUntil();
        budget.succeeded();
        budget.succeeded();
        assertThat(told).hasSize(2).last().isEqualTo("resumed");
        budget.exceeded("REQUEST_LIMIT_EXCEEDED");
        assertThat(Duration.between(clock.now, budget.pausedUntil())).isEqualTo(Duration.ofMinutes(5));
        assertThat(told).hasSize(3);
    }

    @Test
    void theUsageIsReadFromTheHeaderSalesforceSendsWithEveryAnswer() {
        assertThat(budget.remaining()).isEqualTo(-1);
        budget.observe("api-usage=14438/15000");
        assertThat(budget.remaining()).isEqualTo(562);
        assertThat(budget.usage()).isEqualTo("api-usage 14438/15000");
        budget.observe(null);
        budget.observe("per-app-api-usage=3/100(appName=poc)");
        assertThat(budget.remaining()).isEqualTo(562);
    }

    @Test
    void theAllowanceRefusingIsToldFromOtherRefusals() {
        assertThat(SalesforceBudget.isLimit("[{\"message\":\"TotalRequests Limit exceeded.\",\"errorCode\":\"REQUEST_LIMIT_EXCEEDED\"}]")).isTrue();
        assertThat(SalesforceBudget.isLimit("sf:REQUEST_LIMIT_EXCEEDED: TotalRequests Limit exceeded.")).isTrue();
        assertThat(SalesforceBudget.isLimit("[{\"errorCode\":\"INVALID_EMAIL_ADDRESS\"}]")).isFalse();
        assertThat(SalesforceBudget.isLimit(null)).isFalse();
    }

    @Test
    void aJobBacksOffAfterEachFailureInARowAndGoesAtOnceAfterASuccess() {
        var backoff = new Backoff(clock, Duration.ofSeconds(10), Duration.ofMinutes(10));
        assertThat(backoff.ready()).isTrue();
        var waits = new ArrayList<Duration>();
        for (int i = 0; i < 8; i++) {
            backoff.failed();
            assertThat(backoff.ready()).isFalse();
            waits.add(Duration.between(clock.now, backoff.next()));
            clock.now = backoff.next();
            assertThat(backoff.ready()).isTrue();
        }
        assertThat(waits).containsExactly(Duration.ofSeconds(10), Duration.ofSeconds(20), Duration.ofSeconds(40),
                Duration.ofSeconds(80), Duration.ofSeconds(160), Duration.ofSeconds(320), Duration.ofMinutes(10),
                Duration.ofMinutes(10));
        assertThat(backoff.failures()).isEqualTo(8);
        backoff.succeeded();
        assertThat(backoff.ready()).isTrue();
        backoff.failed();
        assertThat(Duration.between(clock.now, backoff.next())).isEqualTo(Duration.ofSeconds(10));
    }
}
