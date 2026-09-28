package io.mateu.ecdemo1.mdm.salesforce;

import io.mateu.ecdemo1.integration.model.usage.ApiCalls;
import io.mateu.ecdemo1.integration.model.usage.ApiUsage;
import io.mateu.ecdemo1.mdm.config.MdmProperties;
import io.mateu.ecdemo1.mdm.store.ApiCallHour;
import io.mateu.ecdemo1.mdm.store.ApiCallHourRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * How much of the org's daily API allowance is spent, and how much of that was the MDM — the
 * answer to "who spent the 15,000?" that the org's own total cannot give. Salesforce's header says
 * the total on every answer ({@link SalesforceBudget}); {@link ApiCalls} counts ours by purpose.
 *
 * <p>Nothing here calls Salesforce on its own account except {@link #refreshLimits()}: one call, at
 * most every quarter of an hour, and only when no answer has said the total for that long. The
 * consoles' header reads {@link #usage()}, which is numbers already known — a page view costs the org
 * nothing.
 *
 * <p>The counts are saved every minute so a restart does not forget the calls that still count in
 * the rolling 24 hours. One replica: two would each save their own counts over the other's.
 */
@Component
@Slf4j
public class SalesforceUsage {

    /** Below this, a net under the events spends more than it catches: the local stack once polled every 20s. */
    static final Duration SHORTEST_SENSIBLE_POLL = Duration.ofMinutes(5);
    static final Duration STALE = Duration.ofMinutes(15);

    final SalesforceClient salesforce;
    final ApiCallHourRepository saved;
    final MdmProperties properties;
    final Clock clock;

    public SalesforceUsage(SalesforceClient salesforce, ApiCallHourRepository saved, MdmProperties properties,
                           Clock clock, MeterRegistry registry) {
        this.salesforce = salesforce;
        this.saved = saved;
        this.properties = properties;
        this.clock = clock;
        var budget = salesforce.budget();
        Gauge.builder("salesforce.org.api.used", budget, b -> b.used() == null ? Double.NaN : b.used())
                .description("The org's API calls in the rolling 24 hours, as Salesforce last said").register(registry);
        Gauge.builder("salesforce.org.api.max", budget, b -> b.max() == null ? Double.NaN : b.max())
                .description("The org's daily API allowance, as Salesforce last said").register(registry);
        Gauge.builder("salesforce.budget.paused", budget, b -> b.open() ? 0 : 1)
                .description("1 while the MDM holds its calls because the allowance is spent").register(registry);
    }

    /** What the calls the MDM made still count for: put back from the database, and a word on the poll. */
    @EventListener(ApplicationReadyEvent.class)
    public void restore() {
        try {
            var since = clock.instant().minus(ApiCalls.WINDOW);
            salesforce.calls().restore(saved.findByHourGreaterThanEqual(since).stream()
                    .map(h -> new ApiCalls.Bucket(h.hour, h.purpose, ApiCalls.Outcome.valueOf(h.outcome.toUpperCase()), h.calls))
                    .toList());
        } catch (RuntimeException e) {
            log.warn("Could not read the Salesforce calls counted before this start: {}", e.getMessage());
        }
        if (salesforce.enabled() && properties.poll() != null && properties.poll().compareTo(SHORTEST_SENSIBLE_POLL) < 0) {
            log.warn("mdm.poll is {}: every poll is a Salesforce call out of an org allowance of 15,000 a day, shared "
                    + "with every environment using the same org — {} calls a day from this poll alone. "
                    + "The Pub/Sub events bring the merges; the poll is only a net. Use {} or more.",
                    properties.poll(), Duration.ofDays(1).dividedBy(properties.poll()), SHORTEST_SENSIBLE_POLL);
        }
    }

    @Scheduled(fixedDelayString = "${mdm.usage-save:60s}", initialDelayString = "${mdm.usage-save:60s}")
    public void save() {
        try {
            saved.saveAll(salesforce.calls().live().stream()
                    .map(b -> new ApiCallHour(b.hour(), b.purpose(), b.outcome().name(), b.calls()))
                    .toList());
            saved.deleteOlderThan(clock.instant().minus(ApiCalls.WINDOW).minus(Duration.ofHours(1)));
        } catch (RuntimeException e) {
            log.debug("Could not save the Salesforce call counts: {}", e.getMessage());
        }
    }

    /** The org's total, asked when no answer has said it for a quarter of an hour — one call, if any. */
    @Scheduled(fixedDelayString = "${mdm.usage-limits-check:5m}", initialDelayString = "${mdm.usage-limits-check:5m}")
    public void refreshLimits() {
        var seen = salesforce.budget().seenAt();
        if (!salesforce.available() || (seen != null && seen.isAfter(clock.instant().minus(STALE)))) {
            return;
        }
        try {
            salesforce.refreshLimits();
        } catch (RuntimeException e) {
            log.debug("Could not ask Salesforce for its limits: {}", e.getMessage());
        }
    }

    public ApiUsage usage() {
        var calls = salesforce.calls();
        var budget = salesforce.budget();
        // The org does not count token requests against the allowance: nor do we, in "ours".
        var ours = calls.total(SalesforceClient.Purpose.TOKEN);
        var used = budget.used();
        return new ApiUsage("salesforce", "customer-mdm", used, budget.max(), budget.seenAt(), ours, calls.byPurpose(),
                null, used == null ? null : Math.max(0, used - ours), calls.total(ApiCalls.Outcome.LIMITED),
                calls.total(ApiCalls.Outcome.ERROR), salesforce.enabled() && !budget.open(), budget.pausedUntil(), null,
                null, calls.lastHour(SalesforceClient.Purpose.TOKEN), calls.previousHour(SalesforceClient.Purpose.TOKEN),
                budget.usedAnHourAgo());
    }
}
