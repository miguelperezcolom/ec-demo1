package io.mateu.ecdemo1.contenthub.infra;

import io.mateu.ecdemo1.contenthub.application.health.ContentHealth;
import io.mateu.ecdemo1.contenthub.application.health.HealthSettings;
import io.mateu.ecdemo1.contenthub.application.health.LinkChecker;
import io.mateu.ecdemo1.contenthub.application.out.AltTextGenerator;
import io.mateu.ecdemo1.contenthub.application.out.HttpProbe;
import io.mateu.ecdemo1.contenthub.application.out.UiSources;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.health.HealthCheck;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.domain.media.MediaAsset;
import io.mateu.ecdemo1.contenthub.infra.in.jobs.HealthJobs;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgEmployeeDirectory;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgHealthRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgHomePages;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgItemRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgMediaDescriptions;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgMediaRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgMenuRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgNavigationRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgNewsletterRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgSlotRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgSurveyRepository;
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

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * «Salud del contenido» against a real PostgreSQL: the night's run is claimed once however often the timer
 * fires, findings keep their identity and first-seen date across runs, «revisado» marks, deleting an orphan
 * and the text alternative with a stub AI. No network: the link checker's HTTP client is a stub too.
 */
class ContentHealthTest {

    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    static JdbcTemplate jdbc;

    static final Instant NIGHT = Instant.parse("2026-11-02T02:15:00Z");

    /** The AI, stubbed: records what it was sent and answers what it is told. */
    static final class StubAi implements AltTextGenerator {
        final List<byte[]> images = new ArrayList<>();
        final List<String> contexts = new ArrayList<>();
        final List<String> tokens = new ArrayList<>();
        String answer = "«Recepcionista atendiendo a una familia en el mostrador del hotel»";

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public String suggest(byte[] image, String contentType, String context, String authorization) {
            images.add(image);
            contexts.add(context);
            tokens.add(authorization);
            return answer;
        }
    }

    NotificationsTest.MutableClock clock;
    PgItemRepository items;
    PgMediaRepository media;
    PgMediaDescriptions descriptions;
    PgHealthRepository store;
    StubAi ai;
    AtomicInteger requests;
    ContentHealth health;

    static final Reader EDITOR = new Reader("eva", "Eva", EmployeeAttributes.NONE, Set.of(EditorialPermission.EDIT), null);
    static final Reader PUBLISHER = new Reader("pablo", "Pablo", EmployeeAttributes.NONE, Set.of(EditorialPermission.PUBLISH), null);
    static final Reader ADMIN = new Reader("demo", "Demo", EmployeeAttributes.NONE, Set.of(EditorialPermission.ADMIN), null);

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
        for (var t : List.of("ch_item", "ch_media", "ch_media_alt", "ch_health_run", "ch_health_finding",
                "ch_health_snooze", "ch_health_link", "ch_home_page", "ch_slot", "ch_navigation")) {
            jdbc.update("delete from " + t);
        }
        clock = new NotificationsTest.MutableClock(NIGHT);
        items = new PgItemRepository(jdbc);
        media = new PgMediaRepository(jdbc);
        descriptions = new PgMediaDescriptions(jdbc);
        store = new PgHealthRepository(jdbc);
        ai = new StubAi();
        requests = new AtomicInteger();
        HttpProbe probe = new HttpProbe() {
            @Override
            public Response request(URI uri, String method, Duration timeout, String userAgent) {
                requests.incrementAndGet();
                return new Response(uri.getPath().contains("roto") ? 404 : 200, null);
            }

            @Override
            public List<java.net.InetAddress> resolve(String host) throws java.net.UnknownHostException {
                return List.of(java.net.InetAddress.getByName("93.184.216.34"));
            }
        };
        var settings = HealthSettings.defaults();
        var noSources = UiSources.NONE;
        health = new ContentHealth(new ContentHealth.Sources(items, media, descriptions, new PgSlotRepository(jdbc),
                new PgHomePages(jdbc), new PgNavigationRepository(jdbc), new PgMenuRepository(jdbc), noSources,
                new PgEmployeeDirectory(jdbc), new PgSurveyRepository(jdbc), new PgNewsletterRepository(jdbc),
                new io.mateu.ecdemo1.contenthub.infra.out.persistence.PgNewsletterIssueRepository(jdbc),
                new io.mateu.ecdemo1.contenthub.infra.out.persistence.PgSiteRepository(jdbc), new PgUsageRepository(jdbc)), store, new LinkChecker(probe, store, settings, clock, d -> { }), ai,
                settings, Set.of("ec1.localhost"), clock, Runnable::run);
    }

    void publish(String id, String body) {
        var item = Item.create(id, ItemType.ARTICLE, "eva", "es", new ItemContent(null, Audience.EVERYONE,
                Map.of("es", new Translation("Item " + id, "", body)), List.of(), null, null, false, false, null, null),
                NIGHT.minus(Duration.ofDays(10)));
        item.publish("pablo", true, false, NIGHT.minus(Duration.ofDays(10)));
        items.save(item);
    }

    void image(String id) {
        media.save(new MediaAsset(id, io.mateu.ecdemo1.contenthub.domain.media.MediaKind.IMAGE, "Foto " + id, id + ".png",
                "image/png", 4, null, "eva", NIGHT.minus(Duration.ofDays(10))), new byte[] {(byte) 0x89, 'P', 'N', 'G'});
    }

    long runs() {
        return jdbc.queryForObject("select count(*) from ch_health_run", Long.class);
    }

    @Test
    void the_night_is_run_once_however_often_the_timer_fires_and_its_results_are_stored() throws Exception {
        publish("a", "<p><a href=\"https://web.example/roto\">x</a> [[media:usada]]</p>");
        image("usada");
        image("huerfana");
        var jobs = new HealthJobs(health, clock);
        var enabled = HealthJobs.class.getDeclaredField("enabled");
        enabled.setAccessible(true);
        enabled.set(jobs, true);

        jobs.nightly();
        jobs.nightly();
        clock.advance(Duration.ofHours(1)); // still the same night in Madrid
        jobs.nightly();

        assertThat(runs()).isEqualTo(1);
        assertThat(requests.get()).as("the external link was asked once").isEqualTo(1);
        var summary = health.summary();
        assertThat(summary.open()).containsEntry(HealthCheck.BROKEN_LINKS, 1).containsEntry(HealthCheck.ORPHAN_MEDIA, 1)
                .containsEntry(HealthCheck.MISSING_ALT, 2);
        assertThat(summary.lastRun()).hasValueSatisfying(r -> {
            assertThat(r.trigger()).isEqualTo("NIGHTLY");
            assertThat(r.finishedAt()).isNotNull();
            assertThat(r.counts()).containsEntry("BROKEN_LINKS", 1);
        });

        // two replicas at the same moment of the next night: one of them runs it
        clock.advance(Duration.ofDays(1));
        var pool = Executors.newFixedThreadPool(2);
        var go = new CountDownLatch(1);
        var ran = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                go.await();
                if (health.nightly(LocalDate.of(2026, 11, 3))) {
                    ran.incrementAndGet();
                }
                return null;
            });
        }
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(ran.get()).isEqualTo(1);
        assertThat(runs()).isEqualTo(2);
    }

    @Test
    void a_rerun_keeps_each_findings_identity_and_first_seen_and_drops_what_was_fixed() {
        publish("a", "<p><a href=\"/intranet/noticias/nadie\">x</a><a href=\"/intranet/noticias/tampoco\">y</a></p>");
        health.runNow("eva");
        var first = health.views();
        assertThat(first).hasSize(2);
        var since = first.getFirst().stored().firstSeen();

        clock.advance(Duration.ofHours(5));
        health.runNow("eva");
        assertThat(health.views()).extracting(v -> v.finding().key())
                .containsExactlyInAnyOrderElementsOf(first.stream().map(v -> v.finding().key()).toList());
        assertThat(health.views()).allMatch(v -> v.stored().firstSeen().equals(since));

        publish("a", "<p><a href=\"/intranet/noticias/nadie\">x</a></p>");
        health.runNow("eva");
        assertThat(health.views()).singleElement().satisfies(v -> assertThat(v.finding().problem()).contains("«nadie»"));
    }

    @Test
    void marking_as_reviewed_takes_it_out_of_the_counts_until_its_date_and_needs_edit() {
        publish("a", "<p><a href=\"/intranet/noticias/nadie\">x</a></p>");
        health.runNow("eva");
        var key = health.views().getFirst().finding().key();

        assertThatThrownBy(() -> health.snooze(PUBLISHER, key, 30, null)).isInstanceOf(SecurityException.class);
        health.snooze(EDITOR, key, 7, "lo arregla Comunicación");
        assertThat(health.summary().open()).containsEntry(HealthCheck.BROKEN_LINKS, 0);
        assertThat(health.summary().reviewed()).containsEntry(HealthCheck.BROKEN_LINKS, 1);

        health.runNow("eva");
        assertThat(health.view(key)).hasValueSatisfying(v -> assertThat(v.reviewed()).isTrue());

        clock.advance(Duration.ofDays(8));
        assertThat(health.view(key)).hasValueSatisfying(v -> assertThat(v.reviewed()).isFalse());
        assertThat(health.summary().open()).containsEntry(HealthCheck.BROKEN_LINKS, 1);
    }

    @Test
    void an_orphan_is_deleted_only_by_an_admin_and_only_while_nothing_uses_it() {
        image("huerfana");
        health.runNow("eva");
        assertThat(health.summary().open()).containsEntry(HealthCheck.ORPHAN_MEDIA, 1);

        assertThatThrownBy(() -> health.deleteOrphan(EDITOR, "huerfana")).isInstanceOf(SecurityException.class);
        publish("b", "<p>[[media:huerfana]]</p>");
        assertThatThrownBy(() -> health.deleteOrphan(ADMIN, "huerfana")).hasMessageContaining("ya se usa");
        assertThat(media.find("huerfana")).isPresent();

        jdbc.update("delete from ch_item");
        health.deleteOrphan(ADMIN, "huerfana");
        assertThat(media.find("huerfana")).isEmpty();
        assertThat(health.summary().open()).containsEntry(HealthCheck.ORPHAN_MEDIA, 0);
    }

    @Test
    void the_ai_suggests_a_text_alternative_from_the_image_and_nothing_changes_until_the_editor_applies_it() {
        image("foto");
        publish("a", "<p>[[media:foto]]</p>");
        health.runNow("eva");
        assertThat(health.summary().open()).containsEntry(HealthCheck.MISSING_ALT, 1);

        var suggestion = health.suggestAlt(EDITOR, "foto", "Bearer eva-token");

        assertThat(suggestion).isEqualTo("Recepcionista atendiendo a una familia en el mostrador del hotel");
        assertThat(ai.images.getFirst()).containsExactly((byte) 0x89, 'P', 'N', 'G');
        assertThat(ai.tokens).containsExactly("Bearer eva-token");
        assertThat(ai.contexts.getFirst()).contains("Foto foto").contains("Item a");
        assertThat(descriptions.altText("foto")).as("a suggestion is never applied by itself").isEmpty();
        assertThat(health.summary().open()).containsEntry(HealthCheck.MISSING_ALT, 1);

        assertThatThrownBy(() -> health.applyAlt(PUBLISHER, "foto", suggestion)).isInstanceOf(SecurityException.class);
        health.applyAlt(EDITOR, "foto", "  Recepcionista atendiendo a una familia  ");
        assertThat(descriptions.altText("foto")).contains("Recepcionista atendiendo a una familia");
        assertThat(health.summary().open()).containsEntry(HealthCheck.MISSING_ALT, 0);
        health.runNow("eva");
        assertThat(health.summary().open()).containsEntry(HealthCheck.MISSING_ALT, 0);
    }

    @Test
    void a_long_or_marked_up_answer_is_cleaned_and_a_non_image_is_refused() {
        image("foto");
        ai.answer = "Texto alternativo: <b>" + "palabra ".repeat(60) + "</b>";
        var suggestion = health.suggestAlt(EDITOR, "foto", "Bearer t");
        assertThat(suggestion).doesNotContain("<b>").doesNotStartWith("Texto alternativo").hasSizeLessThanOrEqualTo(ContentHealth.MAX_ALT);

        media.save(MediaAsset.external("video", "Vídeo", "https://www.youtube.com/watch?v=aqz-KE-bpKQ", "eva", NIGHT), null);
        assertThatThrownBy(() -> health.suggestAlt(EDITOR, "video", "Bearer t")).hasMessageContaining("Solo para imágenes");
        assertThat(ai.images).hasSize(1);
    }
}
