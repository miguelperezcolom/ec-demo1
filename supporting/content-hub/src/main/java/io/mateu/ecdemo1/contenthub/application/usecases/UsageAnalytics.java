package io.mateu.ecdemo1.contenthub.application.usecases;

import io.mateu.ecdemo1.contenthub.application.out.AcknowledgementRepository;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.NewsletterTracking;
import io.mateu.ecdemo1.contenthub.application.out.NotificationLog;
import io.mateu.ecdemo1.contenthub.application.out.UsageRepository;
import io.mateu.ecdemo1.contenthub.application.out.UsageRepository.Metric;
import io.mateu.ecdemo1.contenthub.domain.ack.Acknowledgement;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the usage adds up to: per day, off the raw events, and for the editors' dashboard. Days are
 * Madrid's. A day is aggregated again until it is over, and the raw events go after the retention; the
 * daily figures stay. Active readers and adoption are people, which do not add up across days, so they
 * are counted on the raw events — which is why retention must not go under the longest window, 30 days.
 */
@Service
@Slf4j
public class UsageAnalytics {

    public static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    static final int WINDOW_DAYS = 30;
    static final int TOP = 10;

    final UsageRepository usage;
    final EmployeeDirectory directory;
    final ItemRepository items;
    final NewsletterTracking newsletter;
    final AcknowledgementRepository acks;
    final NotificationLog notifications;
    final Clock clock;
    final Duration retention;

    public UsageAnalytics(UsageRepository usage, EmployeeDirectory directory, ItemRepository items,
                          NewsletterTracking newsletter, AcknowledgementRepository acks, NotificationLog notifications,
                          Clock clock, @Value("${content-hub.analytics.retention:P90D}") Duration retention) {
        this.usage = usage;
        this.directory = directory;
        this.items = items;
        this.newsletter = newsletter;
        this.acks = acks;
        this.notifications = notifications;
        this.clock = clock;
        this.retention = retention.compareTo(Duration.ofDays(WINDOW_DAYS)) < 0 ? Duration.ofDays(WINDOW_DAYS) : retention;
    }

    /** Every day since the last one aggregated, today included (it is done again next time), then the purge. */
    public void aggregate() {
        var now = clock.instant();
        var today = now.atZone(ZONE).toLocalDate();
        var from = usage.lastAggregatedDay()
                .or(() -> usage.firstEvent().map(first -> first.atZone(ZONE).toLocalDate()))
                .orElse(today);
        for (var day = from; !day.isAfter(today); day = day.plusDays(1)) {
            usage.aggregate(day, start(day), start(day.plusDays(1)));
        }
        var purged = usage.purgeBefore(now.minus(retention));
        if (purged > 0) {
            log.info("Usage: {} raw event(s) older than {} purged", purged, retention);
        }
    }

    static Instant start(LocalDate day) {
        return day.atStartOfDay(ZONE).toInstant();
    }

    public record Adoption(String label, long active, long total) {

        public double percent() {
            return total == 0 ? 0 : active * 100d / total;
        }
    }

    public record Overview(long active7, long active30, long employees, long views, long searches, double emptyPercent,
                           Map<LocalDate, Long> viewsPerDay, List<UsageRepository.Ranked> topItems,
                           List<UsageRepository.Ranked> topSearches, List<UsageRepository.Ranked> emptySearches,
                           List<Adoption> byHotel, List<Adoption> byDepartment,
                           List<NewsletterTracking.EditionStats> newsletters) {
    }

    /** The dashboard's figures over the last 30 days, as of the last aggregation. */
    public Overview overview() {
        var now = clock.instant();
        var from = now.atZone(ZONE).toLocalDate().minusDays(WINDOW_DAYS - 1);
        var active30 = usage.activeReaders(now.minus(Duration.ofDays(30)));
        var employees = directory.all().stream().filter(Employee::enabled).toList();
        var searches = usage.total(Metric.SEARCHES, from);
        var empty = usage.total(Metric.SEARCHES_EMPTY, from);
        return new Overview(usage.activeReaders(now.minus(Duration.ofDays(7))).size(), active30.size(), employees.size(),
                usage.total(Metric.VIEWS, from), searches, searches == 0 ? 0 : empty * 100d / searches,
                everyDay(usage.perDay(Metric.VIEWS, from), from, now.atZone(ZONE).toLocalDate()),
                usage.top(Metric.VIEWS_BY_ITEM, from, TOP).stream()
                        .map(r -> new UsageRepository.Ranked(title(r.key()), r.value())).toList(),
                usage.top(Metric.SEARCHES_BY_QUERY, from, TOP), usage.top(Metric.SEARCHES_EMPTY_BY_QUERY, from, TOP),
                adoption(employees, active30, Attribute.HOTEL, "hotel", "(servicios centrales)"),
                adoption(employees, active30, Attribute.DEPARTMENT, "department", "(sin departamento)"),
                newsletter.recentEditions(TOP));
    }

    /** A day nobody came is a zero on the chart, not a gap the line jumps over. */
    static Map<LocalDate, Long> everyDay(Map<LocalDate, Long> figures, LocalDate from, LocalDate to) {
        var out = new LinkedHashMap<LocalDate, Long>();
        for (var day = from; !day.isAfter(to); day = day.plusDays(1)) {
            out.put(day, figures.getOrDefault(day, 0L));
        }
        return out;
    }

    String title(String itemId) {
        return items.find(itemId).map(i -> i.draft().in(i.defaultLanguage(), i.defaultLanguage()).title()).orElse(itemId);
    }

    /** Per value of an attribute: how many of its enabled employees have read anything in the window. */
    static List<Adoption> adoption(Collection<Employee> employees, Set<String> active, Attribute attribute, String labelKey,
                                   String none) {
        var total = new LinkedHashMap<String, Long>();
        var reading = new LinkedHashMap<String, Long>();
        for (var e : employees) {
            var label = e.attributes().get(attribute).map(code -> e.label(labelKey, code)).orElse(none);
            total.merge(label, 1L, Long::sum);
            if (active.contains(e.userId())) {
                reading.merge(label, 1L, Long::sum);
            }
        }
        var out = new ArrayList<Adoption>();
        total.forEach((label, n) -> out.add(new Adoption(label, reading.getOrDefault(label, 0L), n)));
        out.sort(Comparator.comparingLong(Adoption::total).reversed().thenComparing(Adoption::label));
        return out.size() <= 15 ? out : out.subList(0, 15);
    }

    public record ItemFigures(long views, long uniqueReaders, long confirmations, long notified) {
    }

    /** An item's own figures, for its form. */
    public ItemFigures ofItem(String itemId, int confirmationVersion) {
        var stats = usage.itemStats(itemId);
        var confirmations = confirmationVersion == 0 ? 0
                : acks.users(itemId, Acknowledgement.Type.CONFIRMATION, confirmationVersion).size();
        return new ItemFigures(stats.views(), stats.uniqueReaders(), confirmations, notifications.readersTold(itemId));
    }
}
