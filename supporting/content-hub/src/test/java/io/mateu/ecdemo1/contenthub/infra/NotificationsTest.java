package io.mateu.ecdemo1.contenthub.infra;

import io.mateu.ecdemo1.contenthub.application.directory.DirectoryUpdates;
import io.mateu.ecdemo1.contenthub.application.directory.EmployeeMessage;
import io.mateu.ecdemo1.contenthub.application.events.DomainEvents;
import io.mateu.ecdemo1.contenthub.application.events.ProjectionListener;
import io.mateu.ecdemo1.contenthub.application.out.Mailer;
import io.mateu.ecdemo1.contenthub.application.out.NotificationLog;
import io.mateu.ecdemo1.contenthub.application.out.NotificationPreferences;
import io.mateu.ecdemo1.contenthub.application.out.NotificationSender;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.ItemCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.ItemNotifications;
import io.mateu.ecdemo1.contenthub.application.usecases.ReaderActions;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.application.visibility.Projection;
import io.mateu.ecdemo1.contenthub.application.visibility.ProjectionCache;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.domain.shared.DomainEvent;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgAcknowledgementRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgContentIndex;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgEmployeeDirectory;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgItemRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgNamedAudienceRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgNotificationLog;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgNotificationPreferences;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgSlotRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The notifications against a real PostgreSQL and real transactions: who is told (those the visibility
 * function says can see it, as they want to be told), once, in batches that survive being cut short.
 */
class NotificationsTest {

    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    static JdbcTemplate jdbc;
    static TransactionTemplate transactions;

    static final Reader PUBLISHER = new Reader("pablo", "Pablo", EmployeeAttributes.NONE,
            EnumSet.allOf(EditorialPermission.class), null);
    static final Audience RECEPCION = Audience.in(Attribute.DEPARTMENT, "RECEPCION");

    MutableClock clock;
    ItemCommands items;
    PgNotificationPreferences preferences;
    PgNotificationLog log;
    ReaderActions readers;
    DirectoryUpdates directory;
    PgEmployeeDirectory employees;
    Visibility visibility;
    PgAcknowledgementRepository acks;
    PgItemRepository itemRepository;
    PgContentIndex index;
    final List<NotificationSender.ReaderNotification> sent = new ArrayList<>();
    final List<String> resolved = new ArrayList<>();
    final List<String> mails = new ArrayList<>();
    /** Written to fail the next send when this many have gone. */
    int failSendAfter = Integer.MAX_VALUE;
    boolean relayDown;

    final NotificationSender sender = new NotificationSender() {
        @Override
        public void send(ReaderNotification n) {
            if (failSendAfter-- <= 0) {
                throw new IllegalStateException("The outbox is not there");
            }
            sent.add(n);
        }

        @Override
        public void resolve(String subject, String by, Instant at) {
            resolved.add(subject);
        }
    };
    final Mailer mailer = (to, subject, html) -> {
        if (relayDown) {
            throw new IllegalStateException("The relay does not answer");
        }
        mails.add(subject + " → " + to);
    };

    @BeforeAll
    static void start() {
        PG.start();
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        jdbc = new JdbcTemplate(ds);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(ds));
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
        for (var t : List.of("ch_item", "ch_named_audience", "ch_ack", "ch_employee", "ch_profile", "ch_item_doc",
                "ch_block_doc", "ch_slot_variant_doc", "ch_item_media", "ch_projection_state", "ch_chunk",
                "ch_notification_job", "ch_notification_sent", "ch_notification_pref")) {
            jdbc.update("delete from " + t);
        }
        clock = new MutableClock(Instant.parse("2026-10-31T10:00:00Z"));
        itemRepository = new PgItemRepository(jdbc);
        employees = new PgEmployeeDirectory(jdbc);
        index = new PgContentIndex(jdbc);
        acks = new PgAcknowledgementRepository(jdbc);
        log = new PgNotificationLog(jdbc);
        preferences = new PgNotificationPreferences(jdbc);
        var named = new NamedAudiences(new PgNamedAudienceRepository(jdbc));
        var cache = new ProjectionCache();
        var projection = new Projection(index, itemRepository, new PgSlotRepository(jdbc), employees, named, cache);
        var listener = new ProjectionListener(projection, cache);
        var events = new DomainEvents(e -> { }, e -> listener.on((DomainEvent) e));
        visibility = new Visibility(index, projection, clock);
        items = new ItemCommands(itemRepository, events, employees, named, clock);
        directory = new DirectoryUpdates(employees, projection, events);
        employee("ana", "RECEPCION", "es");
        employee("jonas", "RECEPCION", "de");
        employee("klaus", "COCINA", "de");
    }

    ItemNotifications notifications(int batchSize) {
        return new ItemNotifications(itemRepository, employees, visibility, index, acks, log, preferences, sender, mailer,
                transactions, clock, Duration.ofHours(24), Duration.ofDays(2), Duration.ofDays(30), batchSize,
                "https://intranet.example");
    }

    ItemNotifications notifications() {
        return notifications(200);
    }

    void employee(String id, String department, String language) {
        directory.apply(new EmployeeMessage(id + "-" + System.nanoTime(), EmployeeMessage.UPSERTED, id, id,
                id + "@example.com", true, null, null, Map.of("department", department, "language", language), null, null));
    }

    static ItemContent content(Audience audience) {
        return new ItemContent(null, audience, Map.of("es", new Translation("Turnos", "Los turnos de noviembre", "<p>x</p>"),
                "de", new Translation("Schichten", "Die Schichten", "<p>x</p>")), List.of(), null, null, false, false, null, null);
    }

    String publish(ItemType type, ItemContent content) {
        var id = items.create(PUBLISHER, type, "es", content);
        items.publish(PUBLISHER, id, false);
        return id;
    }

    List<String> toldInApp() {
        return sent.stream().map(NotificationSender.ReaderNotification::userId).sorted().toList();
    }

    @Test
    void a_notice_reaches_only_the_readers_who_can_see_it_in_their_language() {
        var id = publish(ItemType.NOTICE, content(RECEPCION));

        notifications().fanOut();

        assertThat(toldInApp()).containsExactly("ana", "jonas");
        var ana = sent.stream().filter(n -> n.userId().equals("ana")).findFirst().orElseThrow();
        var jonas = sent.stream().filter(n -> n.userId().equals("jonas")).findFirst().orElseThrow();
        assertThat(ana.title()).isEqualTo("Turnos");
        assertThat(ana.body()).isEqualTo("Los turnos de noviembre");
        assertThat(jonas.title()).isEqualTo("Schichten");
        assertThat(ana.link()).isEqualTo("/intranet/noticias/" + id);
        assertThat(ana.dedupKey()).isEqualTo("intranet:published:" + id + ":v1:ana");
        assertThat(ana.subject()).isEqualTo("intranet/item/" + id + "/ana");
    }

    @Test
    void by_default_notices_notify_and_nothing_else_does_unless_the_editor_says_so() {
        publish(ItemType.ARTICLE, content(Audience.EVERYONE));
        notifications().fanOut();
        assertThat(sent).isEmpty();

        publish(ItemType.ARTICLE, content(Audience.EVERYONE).withNotification(true, false));
        notifications().fanOut();
        assertThat(toldInApp()).containsExactly("ana", "jonas", "klaus");

        sent.clear();
        publish(ItemType.NOTICE, content(Audience.EVERYONE).withNotification(false, false));
        notifications().fanOut();
        assertThat(sent).isEmpty();
    }

    @Test
    void an_item_version_reaches_a_reader_once_and_a_new_version_once_more() {
        var id = publish(ItemType.NOTICE, content(RECEPCION));
        notifications().fanOut();
        notifications().fanOut();
        // even a job done again, as after a crash just before it was finished
        jdbc.update("update ch_notification_job set done_at = null");
        notifications().fanOut();
        assertThat(toldInApp()).containsExactly("ana", "jonas");

        clock.advance(Duration.ofHours(1));
        items.edit(PUBLISHER, id, content(RECEPCION));
        items.publish(PUBLISHER, id, false);
        notifications().fanOut();

        assertThat(toldInApp()).containsExactly("ana", "ana", "jonas", "jonas");
        assertThat(sent.stream().map(NotificationSender.ReaderNotification::dedupKey).distinct()).hasSize(4);
        assertThat(jdbc.queryForObject("select count(*) from ch_notification_job", Long.class)).isEqualTo(2);
    }

    @Test
    void something_scheduled_tells_nobody_until_its_date_is_crossed_even_if_it_was_crossed_while_down() {
        publish(ItemType.NOTICE, content(RECEPCION).withPublishAt(clock.instant().plus(Duration.ofHours(5))));
        notifications().fanOut();
        assertThat(sent).isEmpty();

        clock.advance(Duration.ofHours(10)); // the crossing itself never saw the service up
        notifications().fanOut();

        assertThat(toldInApp()).containsExactly("ana", "jonas");
    }

    @Test
    void what_became_effective_long_ago_is_not_news_so_turning_this_on_announces_nothing_old() {
        publish(ItemType.NOTICE, content(Audience.EVERYONE));
        clock.advance(Duration.ofHours(25));

        notifications().fanOut();

        assertThat(sent).isEmpty();
    }

    @Test
    void nobody_is_told_of_what_has_already_expired() {
        var c = content(Audience.EVERYONE);
        publish(ItemType.NOTICE, new ItemContent(c.sectionId(), c.audience(), c.translations(), c.blocks(),
                clock.instant().minus(Duration.ofHours(2)), clock.instant().plus(Duration.ofHours(1)), false, false, null, null));
        clock.advance(Duration.ofHours(2));

        notifications().fanOut();

        assertThat(sent).isEmpty();
    }

    @Test
    void it_waits_for_the_projection_as_otherwise_nobody_can_see_the_item_and_everyone_is_skipped() {
        var id = publish(ItemType.NOTICE, content(RECEPCION));
        jdbc.update("delete from ch_projection_state");

        notifications().fanOut();

        assertThat(sent).isEmpty();
        assertThat(log.pendingJobs(10)).hasSize(1);

        jdbc.update("insert into ch_projection_state (subject, version, applied_at) values (?, 1, now())", "item:" + id);
        notifications().fanOut();

        assertThat(toldInApp()).containsExactly("ana", "jonas");
        assertThat(log.pendingJobs(10)).isEmpty();
    }

    @Test
    void a_reader_who_opted_out_of_the_app_is_not_told_there_and_is_still_mailed_and_the_other_way_round() {
        preferences.save("ana", new NotificationPreferences.Preference(false, true));
        preferences.save("jonas", new NotificationPreferences.Preference(true, false));

        publish(ItemType.NOTICE, content(RECEPCION).withNotification(true, true));
        notifications().fanOut();

        assertThat(toldInApp()).containsExactly("jonas");
        assertThat(mails).singleElement().asString().contains("Turnos").contains("ana@example.com")
                .doesNotContain("jonas@example.com");
    }

    @Test
    void only_important_items_are_mailed_one_message_per_language() {
        publish(ItemType.NOTICE, content(Audience.EVERYONE));
        notifications().fanOut();
        assertThat(mails).isEmpty();

        publish(ItemType.NOTICE, content(Audience.EVERYONE).withNotification(null, true));
        notifications().fanOut();

        assertThat(mails).hasSize(2);
        assertThat(mails).anyMatch(m -> m.startsWith("Turnos") && m.contains("ana@"));
        assertThat(mails).anyMatch(m -> m.startsWith("Schichten") && m.contains("jonas@") && m.contains("klaus@"));
    }

    @Test
    void someone_no_longer_employed_is_told_nothing() {
        directory.apply(new EmployeeMessage("off-" + System.nanoTime(), EmployeeMessage.UPSERTED, "ana", "ana",
                "ana@example.com", false, null, null, Map.of("department", "RECEPCION", "language", "es"), null, null));

        publish(ItemType.NOTICE, content(RECEPCION));
        notifications().fanOut();

        assertThat(toldInApp()).containsExactly("jonas");
    }

    @Test
    void a_batch_that_fails_leaves_no_row_so_the_next_pass_tells_everyone_once() {
        publish(ItemType.NOTICE, content(RECEPCION));

        failSendAfter = 1;
        notifications().fanOut();
        // the row and the message are one transaction: nothing of the failed batch counts as told
        assertThat(jdbc.queryForObject("select count(*) from ch_notification_sent", Long.class)).isZero();
        assertThat(log.pendingJobs(10)).singleElement().extracting(NotificationLog.Job::attempts).isEqualTo(1);

        sent.clear();
        failSendAfter = Integer.MAX_VALUE;
        notifications().fanOut();

        assertThat(toldInApp()).containsExactly("ana", "jonas");
        assertThat(log.pendingJobs(10)).isEmpty();
    }

    @Test
    void a_job_cut_short_between_batches_carries_on_where_it_was() {
        publish(ItemType.NOTICE, content(Audience.EVERYONE));

        failSendAfter = 1;
        notifications(1).fanOut();
        assertThat(sent).hasSize(1);
        var first = sent.getFirst().userId();

        failSendAfter = Integer.MAX_VALUE;
        notifications(1).fanOut();

        assertThat(toldInApp()).containsExactly("ana", "jonas", "klaus");
        assertThat(sent.stream().filter(n -> n.userId().equals(first))).hasSize(1);
    }

    @Test
    void a_relay_that_is_down_is_tried_again_without_telling_the_app_twice() {
        publish(ItemType.NOTICE, content(RECEPCION).withNotification(true, true));

        relayDown = true;
        notifications().fanOut();
        assertThat(toldInApp()).containsExactly("ana", "jonas");
        assertThat(mails).isEmpty();
        assertThat(log.pendingJobs(10)).hasSize(1);

        relayDown = false;
        notifications().fanOut();

        assertThat(toldInApp()).containsExactly("ana", "jonas");
        assertThat(mails).hasSize(2);
        assertThat(log.pendingJobs(10)).isEmpty();
    }

    @Test
    void a_job_whose_version_is_no_longer_the_published_one_tells_nobody() {
        var id = publish(ItemType.NOTICE, content(RECEPCION));
        log.enqueue(id, 1, clock.instant());
        items.retire(PUBLISHER, id);

        notifications().fanOut();

        assertThat(sent).isEmpty();
        assertThat(log.pendingJobs(10)).isEmpty();
    }

    ItemContent toConfirm(Instant deadline) {
        var c = content(RECEPCION).withNotification(false, false);
        return new ItemContent(c.sectionId(), c.audience(), c.translations(), c.blocks(), null, null, false, true, deadline,
                null, false, false);
    }

    @Test
    void one_reminder_to_who_can_see_it_and_has_not_confirmed_as_its_deadline_approaches() {
        var id = publish(ItemType.NOTICE, toConfirm(clock.instant().plus(Duration.ofDays(5))));
        notifications().remindDue();
        assertThat(sent).as("the deadline is still far").isEmpty();

        clock.advance(Duration.ofDays(4));
        var readerActions = new ReaderActions(visibility, null, acks, itemRepository, clock);
        readerActions.confirm(new Reader("ana", "ana", employees.find("ana").orElseThrow().attributes(), Set.of(), null), id);

        notifications().remindDue();
        notifications().remindDue();

        assertThat(sent).singleElement().satisfies(n -> {
            assertThat(n.userId()).isEqualTo("jonas");
            assertThat(n.kind()).isEqualTo(NotificationLog.Kind.CONFIRMATION_DUE);
            assertThat(n.title()).contains("Schichten");
            assertThat(n.subject()).isEqualTo("intranet/confirmation/" + id + "/jonas");
        });
    }

    @Test
    void no_reminder_once_the_deadline_has_passed_or_to_who_opted_out() {
        preferences.save("jonas", new NotificationPreferences.Preference(false, false));
        publish(ItemType.NOTICE, toConfirm(clock.instant().plus(Duration.ofDays(1))));
        notifications().remindDue();
        assertThat(toldInApp()).containsExactly("ana");

        sent.clear();
        jdbc.update("delete from ch_notification_sent");
        clock.advance(Duration.ofDays(2));
        notifications().remindDue();

        assertThat(sent).isEmpty();
    }

    @Test
    void opening_the_item_closes_its_news_for_that_reader_only_and_never_through_ver_como() {
        var id = publish(ItemType.NOTICE, content(RECEPCION));
        notifications().fanOut();
        var ana = employees.find("ana").orElseThrow().attributes();

        notifications().opened(new Reader("ana", "ana", ana, Set.of(), "jonas"), id);
        assertThat(resolved).isEmpty();

        notifications().opened(new Reader("ana", "ana", ana, Set.of(), null), id);
        notifications().opened(new Reader("ana", "ana", ana, Set.of(), null), id);

        assertThat(resolved).containsExactly("intranet/item/" + id + "/ana");
    }

    @Test
    void confirming_closes_the_reminder_and_not_the_news() {
        var id = publish(ItemType.NOTICE, toConfirm(clock.instant().plus(Duration.ofDays(1))));
        notifications().remindDue();

        notifications().confirmed(new Reader("ana", "ana", EmployeeAttributes.NONE, Set.of(), null), id);

        assertThat(resolved).containsExactly("intranet/confirmation/" + id + "/ana");
    }

    @Test
    void what_is_no_longer_readable_or_too_old_closes_by_itself() {
        var retired = publish(ItemType.NOTICE, content(RECEPCION));
        notifications().fanOut();
        items.retire(PUBLISHER, retired);

        notifications().closeStale();
        assertThat(resolved).containsExactlyInAnyOrder("intranet/item/" + retired + "/ana", "intranet/item/" + retired + "/jonas");

        resolved.clear();
        var old = publish(ItemType.NOTICE, content(RECEPCION));
        notifications().fanOut();
        notifications().closeStale();
        assertThat(resolved).as("still readable and recent").isEmpty();

        clock.advance(Duration.ofDays(31));
        notifications().closeStale();
        assertThat(resolved).containsExactlyInAnyOrder("intranet/item/" + old + "/ana", "intranet/item/" + old + "/jonas");
    }

    static final class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
