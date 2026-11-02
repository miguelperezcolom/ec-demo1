package io.mateu.ecdemo1.contenthub.infra;

import io.mateu.ecdemo1.contenthub.application.out.NewsletterTracking;
import io.mateu.ecdemo1.contenthub.application.out.UsageRepository;
import io.mateu.ecdemo1.contenthub.application.out.UsageRepository.Metric;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.UsageAnalytics;
import io.mateu.ecdemo1.contenthub.application.usecases.UsageTracker;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgAcknowledgementRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgEmployeeDirectory;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgItemRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgNewsletterTracking;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgNotificationLog;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgUsageRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** What the usage adds up to, against a real PostgreSQL: the daily figures, the retention, the dashboard's. */
class AnalyticsTest {

    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    static JdbcTemplate jdbc;

    static final Instant NOON = Instant.parse("2026-10-31T12:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 10, 31);
    static final EmployeeAttributes RECEPTION = new EmployeeAttributes(
            Map.of(Attribute.HOTEL, "TFS01", Attribute.DEPARTMENT, "RECEPCION"), "es");

    NotificationsTest.MutableClock clock;
    PgUsageRepository usage;
    UsageTracker tracker;
    UsageAnalytics analytics;
    PgEmployeeDirectory employees;

    @BeforeAll
    static void start() {
        PG.start();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
        jdbc.execute((java.sql.Connection c) -> {
            ScriptUtils.executeSqlScript(c, new ClassPathResource("schema.sql"));
            return null;
        });
    }

    @AfterAll
    static void stop() {
        PG.stop();
    }

    @BeforeEach
    void wire() {
        for (var t : List.of("ch_usage_event", "ch_usage_daily", "ch_item_reader", "ch_employee", "ch_item", "ch_newsletter",
                "ch_newsletter_event", "ch_ack", "ch_notification_sent")) {
            jdbc.update("delete from " + t);
        }
        clock = new NotificationsTest.MutableClock(NOON);
        usage = new PgUsageRepository(jdbc);
        employees = new PgEmployeeDirectory(jdbc);
        tracker = new UsageTracker(usage, clock);
        analytics = new UsageAnalytics(usage, employees, new PgItemRepository(jdbc), new PgNewsletterTracking(jdbc),
                new PgAcknowledgementRepository(jdbc), new PgNotificationLog(jdbc), clock, Duration.ofDays(90));
    }

    static Reader reader(String id) {
        return new Reader(id, id, RECEPTION, Set.of(), null);
    }

    long raw(String where) {
        return jdbc.queryForObject("select count(*) from ch_usage_event where " + where, Long.class);
    }

    long daily(LocalDate day, String metric, String key) {
        var v = jdbc.queryForList("select value from ch_usage_daily where day = ? and metric = ? and key = ?", Long.class,
                java.sql.Date.valueOf(day), metric, key);
        return v.isEmpty() ? 0 : v.getFirst();
    }

    @Test
    void a_search_keeps_the_profile_and_never_who_and_the_same_words_are_the_same_search() {
        tracker.searched(reader("ana"), "  Vacaciones   VERANO ", 3);

        var row = jdbc.queryForMap("select * from ch_usage_event");
        assertThat(row.get("user_id")).isNull();
        assertThat(row.get("profile_hash")).isEqualTo(RECEPTION.hash());
        assertThat(row.get("query")).isEqualTo("vacaciones verano");
        assertThat(row.get("results")).isEqualTo(3);
    }

    @Test
    void an_empty_search_or_an_editor_looking_through_ver_como_is_not_usage() {
        tracker.searched(reader("ana"), "   ", 0);
        var viewingAs = new Reader("eva", "Eva", RECEPTION, Set.of(), "un perfil");
        tracker.itemViewed(viewingAs, "x");
        tracker.homeVisited(viewingAs);
        tracker.searched(viewingAs, "algo", 1);

        assertThat(raw("true")).isZero();
        assertThat(jdbc.queryForObject("select count(*) from ch_item_reader", Long.class)).isZero();
    }

    @Test
    void a_day_adds_up_its_views_searches_visits_and_readers() {
        tracker.itemViewed(reader("ana"), "a");
        tracker.itemViewed(reader("ana"), "a");
        tracker.itemViewed(reader("luis"), "b");
        tracker.homeVisited(reader("ana"));
        tracker.searched(reader("ana"), "vacaciones", 2);
        tracker.searched(reader("luis"), "Vacaciones", 2);
        tracker.searched(reader("luis"), "wifi", 0);

        analytics.aggregate();

        assertThat(daily(TODAY, Metric.VIEWS, "")).isEqualTo(3);
        assertThat(daily(TODAY, Metric.VIEWS_BY_ITEM, "a")).isEqualTo(2);
        assertThat(daily(TODAY, Metric.VIEWS_BY_ITEM, "b")).isEqualTo(1);
        assertThat(daily(TODAY, Metric.HOME_VISITS, "")).isEqualTo(1);
        assertThat(daily(TODAY, Metric.READERS, "")).isEqualTo(2);
        assertThat(daily(TODAY, Metric.SEARCHES, "")).isEqualTo(3);
        assertThat(daily(TODAY, Metric.SEARCHES_EMPTY, "")).isEqualTo(1);
        assertThat(daily(TODAY, Metric.SEARCHES_BY_QUERY, "vacaciones")).isEqualTo(2);
        assertThat(daily(TODAY, Metric.SEARCHES_EMPTY_BY_QUERY, "wifi")).isEqualTo(1);
    }

    @Test
    void aggregating_again_counts_nothing_twice_and_today_keeps_growing() {
        tracker.itemViewed(reader("ana"), "a");
        analytics.aggregate();
        analytics.aggregate();
        assertThat(daily(TODAY, Metric.VIEWS, "")).isEqualTo(1);

        tracker.itemViewed(reader("ana"), "a");
        analytics.aggregate();

        assertThat(daily(TODAY, Metric.VIEWS, "")).isEqualTo(2);
    }

    @Test
    void days_are_madrids_and_a_gap_in_the_aggregation_is_caught_up() {
        // Madrid is UTC+1 after the 25th of October: 22:30 UTC on the 30th is still the 30th there, 23:30 the 31st
        clock.now = Instant.parse("2026-10-29T12:00:00Z");
        tracker.itemViewed(reader("ana"), "a");
        analytics.aggregate();
        clock.now = Instant.parse("2026-10-30T22:30:00Z");
        tracker.itemViewed(reader("ana"), "b");
        clock.now = Instant.parse("2026-10-30T23:30:00Z");
        tracker.itemViewed(reader("ana"), "c");
        clock.now = NOON;

        analytics.aggregate();

        assertThat(daily(LocalDate.of(2026, 10, 29), Metric.VIEWS, "")).isEqualTo(1);
        assertThat(daily(LocalDate.of(2026, 10, 30), Metric.VIEWS, "")).isEqualTo(1);
        assertThat(daily(TODAY, Metric.VIEWS, "")).isEqualTo(1);
        assertThat(daily(TODAY, Metric.VIEWS_BY_ITEM, "c")).isEqualTo(1);
    }

    @Test
    void the_retention_purges_the_raw_events_and_keeps_the_daily_figures() {
        clock.now = NOON.minus(Duration.ofDays(91));
        tracker.itemViewed(reader("ana"), "old");
        analytics.aggregate();
        clock.now = NOON;
        tracker.itemViewed(reader("ana"), "new");

        analytics.aggregate();

        assertThat(raw("item_id = 'old'")).isZero();
        assertThat(raw("item_id = 'new'")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select sum(value) from ch_usage_daily where metric = 'views.item' and key = 'old'",
                Long.class)).isEqualTo(1);
    }

    @Test
    void a_retention_shorter_than_the_longest_window_is_not_allowed_to_empty_it() {
        var shortRetention = new UsageAnalytics(usage, employees, new PgItemRepository(jdbc), new PgNewsletterTracking(jdbc),
                new PgAcknowledgementRepository(jdbc), new PgNotificationLog(jdbc), clock, Duration.ofDays(1));
        clock.now = NOON.minus(Duration.ofDays(20));
        tracker.homeVisited(reader("ana"));
        clock.now = NOON;

        shortRetention.aggregate();

        assertThat(raw("true")).isEqualTo(1);
    }

    @Test
    void the_overview_counts_active_readers_per_window_and_the_share_of_empty_searches() {
        clock.now = NOON.minus(Duration.ofDays(20));
        tracker.homeVisited(reader("luis"));
        tracker.itemViewed(reader("luis"), "a");
        analytics.aggregate();
        clock.now = NOON.minus(Duration.ofDays(1));
        tracker.itemViewed(reader("ana"), "a");
        tracker.searched(reader("ana"), "vacaciones", 1);
        tracker.searched(reader("ana"), "wifi", 0);
        tracker.searched(reader("ana"), "wifi", 0);
        tracker.searched(reader("ana"), "nóminas", 4);
        analytics.aggregate();
        clock.now = NOON;
        analytics.aggregate();
        employees.upsert(new Employee("ana", "Ana", "a@x", true, RECEPTION));
        employees.upsert(new Employee("luis", "Luis", "l@x", true, RECEPTION));
        employees.upsert(new Employee("marta", "Marta", "m@x", true, new EmployeeAttributes(Map.of(), "es")));
        employees.upsert(new Employee("baja", "Baja", "b@x", false, RECEPTION));

        var o = analytics.overview();

        assertThat(o.active7()).isEqualTo(1);
        assertThat(o.active30()).isEqualTo(2);
        assertThat(o.employees()).isEqualTo(3);
        assertThat(o.views()).isEqualTo(2);
        assertThat(o.searches()).isEqualTo(4);
        assertThat(o.emptyPercent()).isCloseTo(50, within(0.01));
        assertThat(o.viewsPerDay()).hasSize(30).containsEntry(TODAY.minusDays(1), 1L).containsEntry(TODAY, 0L);
        assertThat(o.topItems()).singleElement().isEqualTo(new UsageRepository.Ranked("a", 2));
        assertThat(o.topSearches().getFirst()).isEqualTo(new UsageRepository.Ranked("wifi", 2));
        assertThat(o.emptySearches()).containsExactly(new UsageRepository.Ranked("wifi", 2));
        // the hotel's two employees have both read something; head office's one has not
        assertThat(o.byHotel()).containsExactly(new UsageAnalytics.Adoption("TFS01", 2, 2),
                new UsageAnalytics.Adoption("(servicios centrales)", 0, 1));
        assertThat(o.byDepartment()).containsExactly(new UsageAnalytics.Adoption("RECEPCION", 2, 2),
                new UsageAnalytics.Adoption("(sin departamento)", 0, 1));
    }

    @Test
    void the_overview_of_nothing_at_all_is_zeros() {
        var o = analytics.overview();

        assertThat(o.active30()).isZero();
        assertThat(o.emptyPercent()).isZero();
        assertThat(o.topItems()).isEmpty();
        assertThat(o.newsletters()).isEmpty();
        assertThat(o.viewsPerDay()).hasSize(30).allSatisfy((day, n) -> assertThat(n).isZero());
    }

    @Test
    void an_item_has_its_views_and_unique_readers_since_it_exists_beyond_the_retention() {
        clock.now = NOON.minus(Duration.ofDays(200));
        tracker.itemViewed(reader("ana"), "a");
        clock.now = NOON;
        tracker.itemViewed(reader("ana"), "a");
        tracker.itemViewed(reader("luis"), "a");
        tracker.itemViewed(reader("luis"), "b");
        analytics.aggregate();
        jdbc.update("insert into ch_ack (item_id, version, user_id, type, at) values ('a', 2, 'ana', 'CONFIRMATION', now()), "
                + "('a', 1, 'luis', 'CONFIRMATION', now())");
        jdbc.update("insert into ch_notification_sent (item_id, version, user_id, kind, channel, at) values "
                + "('a', 1, 'ana', 'PUBLISHED', 'IN_APP', now()), ('a', 2, 'ana', 'PUBLISHED', 'IN_APP', now()), "
                + "('a', 2, 'ana', 'PUBLISHED', 'EMAIL', now()), ('a', 2, 'luis', 'CONFIRMATION_DUE', 'IN_APP', now())");

        var figures = analytics.ofItem("a", 2);

        assertThat(figures.views()).isEqualTo(3);
        assertThat(figures.uniqueReaders()).isEqualTo(2);
        assertThat(figures.confirmations()).as("only those of the version that counts").isEqualTo(1);
        assertThat(figures.notified()).isEqualTo(1);
    }

    @Test
    void the_newsletters_totals_per_edition_come_with_their_title() {
        jdbc.update("insert into ch_newsletter (id, doc, status) values ('n1', '{\"title\":\"Boletín 45\"}'::jsonb, 'SENT')");
        var tracking = new PgNewsletterTracking(jdbc);
        tracking.record(new NewsletterTracking.Event("n1", "g1", NewsletterTracking.Kind.OPEN, null));
        tracking.record(new NewsletterTracking.Event("n1", "g2", NewsletterTracking.Kind.OPEN, null));
        tracking.record(new NewsletterTracking.Event("n1", "g1", NewsletterTracking.Kind.CLICK, "a"));

        assertThat(tracking.recentEditions(5)).containsExactly(new NewsletterTracking.EditionStats("n1", "Boletín 45", 2, 1));
    }
}
