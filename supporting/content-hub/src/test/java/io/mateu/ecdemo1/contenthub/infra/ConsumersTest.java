package io.mateu.ecdemo1.contenthub.infra;

import io.mateu.ecdemo1.contenthub.application.directory.DirectoryUpdates;
import io.mateu.ecdemo1.contenthub.application.directory.EmployeeMessage;
import io.mateu.ecdemo1.contenthub.application.events.DomainEvents;
import io.mateu.ecdemo1.contenthub.application.events.ProjectionListener;
import io.mateu.ecdemo1.contenthub.application.out.ContentQuery;
import io.mateu.ecdemo1.contenthub.application.out.Embeddings;
import io.mateu.ecdemo1.contenthub.application.out.VisibleItem;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.ItemCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.NavigationCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.NewsletterService;
import io.mateu.ecdemo1.contenthub.application.usecases.ReaderActions;
import io.mateu.ecdemo1.contenthub.application.usecases.SlotCommands;
import io.mateu.ecdemo1.contenthub.application.visibility.ContentQuestions;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.application.visibility.Projection;
import io.mateu.ecdemo1.contenthub.application.visibility.ProjectionCache;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.domain.shared.DomainEvent;
import io.mateu.ecdemo1.contenthub.domain.slot.SlotVariant;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgAcknowledgementRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgContentIndex;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgEmployeeDirectory;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgFavoritesRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgItemRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgNamedAudienceRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgNavigationRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgNewsletterRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgSlotRepository;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The acceptance criteria against a real PostgreSQL (with pgvector): the application services wired by
 * hand over the real adapters, the projection applied inline. The first test is the one that matters
 * most — an item whose audience the reader does not meet appears in no consumer at all.
 */
class ConsumersTest {

    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static JdbcTemplate jdbc;

    MutableClock clock;
    Visibility visibility;
    Projection projection;
    ProjectionCache cache;
    ItemCommands itemCommands;
    NavigationCommands navigation;
    AudienceCommands audiences;
    SlotCommands slots;
    ReaderActions readerActions;
    NewsletterService newsletter;
    ContentQuestions questions;
    DirectoryUpdates directory;
    PgNewsletterRepository editions;
    final List<String> mails = new ArrayList<>();

    static final Reader PUBLISHER = new Reader("pablo", "Pablo", EmployeeAttributes.NONE,
            EnumSet.allOf(EditorialPermission.class), null);
    static final Reader EDITOR = new Reader("eva", "Eva", EmployeeAttributes.NONE,
            EnumSet.of(EditorialPermission.EDIT), null);

    @BeforeAll
    static void start() {
        PG.start();
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        jdbc = new JdbcTemplate(ds);
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
        for (var t : List.of("ch_item", "ch_navigation", "ch_slot", "ch_named_audience", "ch_newsletter", "ch_favorites",
                "ch_ack", "ch_employee", "ch_profile", "ch_item_doc", "ch_block_doc", "ch_slot_variant_doc",
                "ch_item_media", "ch_projection_state", "ch_chunk")) {
            jdbc.update("delete from " + t);
        }
        clock = new MutableClock(Instant.parse("2026-10-31T10:00:00Z"));
        var items = new PgItemRepository(jdbc);
        var slotRepo = new PgSlotRepository(jdbc);
        var audienceRepo = new PgNamedAudienceRepository(jdbc);
        editions = new PgNewsletterRepository(jdbc);
        var favorites = new PgFavoritesRepository(jdbc);
        var acks = new PgAcknowledgementRepository(jdbc);
        var employees = new PgEmployeeDirectory(jdbc);
        var index = new PgContentIndex(jdbc);
        var named = new NamedAudiences(audienceRepo);
        cache = new ProjectionCache();
        projection = new Projection(index, items, slotRepo, employees, named, cache);
        var listener = new ProjectionListener(projection, cache);
        var events = new DomainEvents(e -> { }, e -> listener.on((DomainEvent) e));
        visibility = new Visibility(index, projection, clock);
        itemCommands = new ItemCommands(items, events, employees, named, clock);
        navigation = new NavigationCommands(new PgNavigationRepository(jdbc), events);
        audiences = new AudienceCommands(audienceRepo, named, employees, events);
        slots = new SlotCommands(slotRepo, events);
        readerActions = new ReaderActions(visibility, favorites, acks, items, clock);
        newsletter = new NewsletterService(editions, employees, visibility, named,
                (to, subject, html) -> mails.add(subject + " → " + to), clock);
        Embeddings fake = (texts, query) -> texts.stream().map(ConsumersTest::vector).toList();
        questions = new ContentQuestions(index, fake, projection, clock);
        directory = new DirectoryUpdates(employees, projection, events);

        employee("ana", "TFS01", "CANARIAS", "RECEPCION", "es");
        employee("jonas", "TFS01", "CANARIAS", "RECEPCION", "de");
        employee("klaus", "PMI01", "BALEARES", "COCINA", "de");
        navigation.add(PUBLISHER, "noticias", null, Map.of("es", "Noticias"));
        navigation.add(PUBLISHER, "rrhh", null, Map.of("es", "RRHH"));
        navigation.add(PUBLISHER, "nominas", "rrhh", Map.of("es", "Nóminas"));
    }

    void employee(String id, String hotel, String region, String department, String language) {
        directory.apply(new EmployeeMessage(id + "-" + System.nanoTime(), EmployeeMessage.UPSERTED, id, id,
                id + "@example.com", true, Map.of("hotel", hotel, "region", region, "department", department,
                "language", language), null));
    }

    static EmployeeAttributes profile(String hotel, String region, String department, String language) {
        return new EmployeeAttributes(Map.of(Attribute.HOTEL, hotel, Attribute.REGION, region,
                Attribute.DEPARTMENT, department), language);
    }

    static final EmployeeAttributes ANA = profile("TFS01", "CANARIAS", "RECEPCION", "es");
    static final EmployeeAttributes JONAS = profile("TFS01", "CANARIAS", "RECEPCION", "de");
    static final EmployeeAttributes KLAUS = profile("PMI01", "BALEARES", "COCINA", "de");

    static Reader reader(String id, EmployeeAttributes a) {
        return new Reader(id, id, a, Set.of(), null);
    }

    String publish(ItemType type, String section, Audience audience, String title, String body) {
        return publish(type, section, audience, Map.of("es", new Translation(title, "Resumen " + title, body)),
                List.of(), null, null, false);
    }

    String publish(ItemType type, String section, Audience audience, Map<String, Translation> translations,
                   List<Block> blocks, Instant publishAt, Instant expireAt, boolean confirm) {
        var id = itemCommands.create(EDITOR, type, "es", new ItemContent(section, audience, translations, blocks,
                publishAt, expireAt, false, confirm, null, null));
        itemCommands.submitForReview(EDITOR, id);
        itemCommands.publish(PUBLISHER, id, false);
        return id;
    }

    static final Audience RECEPCION_CANARIAS = Audience.allOf(Audience.in(Attribute.DEPARTMENT, "RECEPCION"),
            Audience.in(Attribute.REGION, "CANARIAS"));

    @Test
    void an_item_whose_audience_the_reader_does_not_meet_appears_in_no_consumer() {
        var secret = publish(ItemType.ARTICLE, "nominas", RECEPCION_CANARIAS, "Turnos de recepción",
                "<p>Calendario de turnos de recepción en Canarias</p>");
        var klaus = reader("klaus", KLAUS);
        var ana = reader("ana", ANA);
        // the reader who meets it sees it everywhere…
        assertThat(visibility.visibles(ANA, ContentQuery.latest(50)).content()).extracting(VisibleItem::itemId).contains(secret);
        readerActions.toggleFavorite(ana, secret);
        // …and the one who does not, nowhere:
        // page
        assertThat(visibility.item(KLAUS, secret)).isEmpty();
        assertThat(visibility.isVisible(KLAUS, secret)).isFalse();
        // menu
        assertThat(visibility.sectionsWithVisibleContent(KLAUS)).doesNotContain("nominas");
        // listings
        assertThat(visibility.visibles(KLAUS, ContentQuery.latest(50)).content()).extracting(VisibleItem::itemId)
                .doesNotContain(secret);
        assertThat(visibility.visibles(KLAUS, ContentQuery.section("nominas", 0, 50)).total()).isZero();
        // search
        assertThat(visibility.visibles(KLAUS, ContentQuery.search("turnos", 0, 50)).total()).isZero();
        assertThat(visibility.visibles(ANA, ContentQuery.search("turnos", 0, 50)).total()).isEqualTo(1);
        // favourites: he cannot even star it
        assertThatThrownBy(() -> readerActions.toggleFavorite(klaus, secret)).isInstanceOf(SecurityException.class);
        // slot: a variant with the same audience
        slots.registerDescriptor("fo", List.of(new SlotCommands.SlotDescriptor("fo.home.destacado", "Destacado", "")));
        slots.putVariant(PUBLISHER, "fo.home.destacado", new SlotVariant("v1", RECEPCION_CANARIAS, 10, "Turnos", "<p>x</p>", null));
        assertThat(visibility.slot(KLAUS, "fo.home.destacado")).isEmpty();
        assertThat(visibility.slot(ANA, "fo.home.destacado")).isPresent();
        // newsletter
        var editionId = newsletter.compose(PUBLISHER, "Boletín", "", List.of(secret), Audience.EVERYONE);
        var edition = editions.find(editionId).orElseThrow();
        assertThat(newsletter.itemsFor(edition, KLAUS)).isEmpty();
        assertThat(newsletter.itemsFor(edition, ANA)).extracting(VisibleItem::itemId).containsExactly(secret);
        // MCP / the chat's RAG
        questions.embedPending(100);
        assertThat(questions.passagesFor(KLAUS, "turnos de recepción", 5)).isEmpty();
        assertThat(questions.passagesFor(ANA, "turnos de recepción", 5)).extracting("itemId").containsOnly(secret);
    }

    @Test
    void a_block_is_seen_only_by_who_meets_page_and_block_and_the_editor_is_warned() {
        var narrow = Audience.in(Attribute.DEPARTMENT, "COCINA");
        var blocks = List.of(new Block("b1", BlockType.HTML, narrow, "Solo cocina", "<p>x</p>", null, null, null, null, null),
                new Block("b2", BlockType.HTML, null, "Para todos", "<p>y</p>", null, null, null, null, null));
        var content = new ItemContent("noticias", RECEPCION_CANARIAS, Map.of("es", new Translation("Página", "", "")),
                blocks, null, null, false, false, null, null);
        assertThat(itemCommands.warnings(content)).anyMatch(w -> w.contains("Solo cocina"));
        var id = publish(ItemType.PAGE, "noticias", RECEPCION_CANARIAS, content.translations(), blocks, null, null, false);
        assertThat(visibility.visibleBlockIds(ANA, id)).containsExactly("b2");
        assertThat(visibility.visibleBlockIds(KLAUS, id)).isEmpty();
    }

    @Test
    void scheduled_and_expired_need_no_job() {
        var later = publish(ItemType.NOTICE, "noticias", Audience.EVERYONE, Map.of("es", new Translation("Luego", "", "")),
                List.of(), clock.instant().plus(Duration.ofHours(1)), null, false);
        var expiring = publish(ItemType.NOTICE, "noticias", Audience.EVERYONE, Map.of("es", new Translation("Caduca", "", "")),
                List.of(), null, clock.instant().plus(Duration.ofMinutes(30)), false);
        assertThat(visibility.isVisible(ANA, later)).isFalse();
        assertThat(visibility.isVisible(ANA, expiring)).isTrue();
        clock.advance(Duration.ofHours(2));
        assertThat(visibility.isVisible(ANA, later)).isTrue();
        assertThat(visibility.isVisible(ANA, expiring)).isFalse();
    }

    @Test
    void sections_with_nothing_visible_disappear() {
        publish(ItemType.ARTICLE, "nominas", RECEPCION_CANARIAS, "Nómina", "");
        assertThat(visibility.sectionsWithVisibleContent(ANA)).contains("nominas");
        assertThat(visibility.sectionsWithVisibleContent(KLAUS)).isEmpty();
    }

    @Test
    void paging_and_counts_only_count_what_is_visible() {
        for (int i = 0; i < 7; i++) {
            publish(ItemType.ARTICLE, "noticias", i % 2 == 0 ? RECEPCION_CANARIAS : Audience.in(Attribute.HOTEL, "PMI01"),
                    "Noticia de hotel " + i, "<p>hotel</p>");
        }
        var page = visibility.visibles(ANA, ContentQuery.search("hotel", 0, 2));
        assertThat(page.total()).isEqualTo(4);
        assertThat(page.content()).hasSize(2);
        assertThat(visibility.visibles(KLAUS, ContentQuery.search("hotel", 2, 2)).content()).hasSize(1);
    }

    @Test
    void the_readers_language_or_the_default_one() {
        var id = publish(ItemType.ARTICLE, "noticias", Audience.EVERYONE,
                Map.of("es", new Translation("Hola", "", ""), "de", new Translation("Hallo", "", "")),
                List.of(), null, null, false);
        var onlyEs = publish(ItemType.ARTICLE, "noticias", Audience.EVERYONE, "Solo español", "");
        assertThat(visibility.item(JONAS, id).orElseThrow().title()).isEqualTo("Hallo");
        assertThat(visibility.item(JONAS, onlyEs).orElseThrow().title()).isEqualTo("Solo español");
        assertThat(visibility.item(ANA, id).orElseThrow().title()).isEqualTo("Hola");
    }

    @Test
    void attributes_change_what_is_seen_on_the_next_request_and_same_attributes_share_the_cache() {
        var id = publish(ItemType.ARTICLE, "noticias", Audience.in(Attribute.DEPARTMENT, "COCINA"), "Menú", "");
        assertThat(visibility.isVisible(ANA, id)).isFalse();
        employee("ana", "TFS01", "CANARIAS", "COCINA", "es");
        assertThat(visibility.isVisible(profile("TFS01", "CANARIAS", "COCINA", "es"), id)).isTrue();
        assertThat(ANA.hash()).isEqualTo(JONAS.hash());
        cache.invalidateAll();
        cache.get(ANA.hash(), "es", "home", () -> "x");
        cache.get(JONAS.hash(), "es", "home", () -> "y");
        assertThat(cache.hits()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void favourites_hide_and_come_back_with_visibility() {
        var id = publish(ItemType.ARTICLE, "noticias", RECEPCION_CANARIAS, "Fav", "");
        var ana = reader("ana", ANA);
        readerActions.toggleFavorite(ana, id);
        assertThat(readerActions.favorites(ana).content()).hasSize(1);
        audiences.define(PUBLISHER, "rc", "Recepción Canarias", RECEPCION_CANARIAS);
        itemCommands.edit(PUBLISHER, id, new ItemContent("noticias", new Audience.Named("rc"),
                Map.of("es", new Translation("Fav", "", "")), List.of(), null, null, false, false, null, null));
        itemCommands.publish(PUBLISHER, id, false);
        assertThat(readerActions.favorites(ana).content()).hasSize(1);
        audiences.redefine(PUBLISHER, "rc", "Recepción Canarias", Audience.in(Attribute.DEPARTMENT, "COCINA"));
        assertThat(readerActions.favorites(ana).content()).as("redefined away").isEmpty();
        audiences.redefine(PUBLISHER, "rc", "Recepción Canarias", RECEPCION_CANARIAS);
        assertThat(readerActions.favorites(ana).content()).as("and back").hasSize(1);
    }

    @Test
    void a_slot_with_no_visible_variant_answers_nothing_and_priority_wins() {
        slots.registerDescriptor("fo", List.of(new SlotCommands.SlotDescriptor("fo.home.banner", "Banner", "")));
        assertThat(visibility.slot(ANA, "fo.home.banner")).isEmpty();
        slots.putVariant(PUBLISHER, "fo.home.banner", new SlotVariant("all", Audience.EVERYONE, 1, "General", "", null));
        slots.putVariant(PUBLISHER, "fo.home.banner", new SlotVariant("rc", RECEPCION_CANARIAS, 5, "Canarias", "", null));
        assertThat(visibility.slot(ANA, "fo.home.banner").orElseThrow().variantId()).isEqualTo("rc");
        assertThat(visibility.slot(KLAUS, "fo.home.banner").orElseThrow().variantId()).isEqualTo("all");
        slots.registerDescriptor("fo", List.of());
        assertThat(new PgSlotRepository(jdbc).find("fo.home.banner").orElseThrow().orphan()).isTrue();
    }

    @Test
    void the_newsletter_goes_once_per_hash_and_language_and_skips_groups_with_nothing() {
        var rc = publish(ItemType.ARTICLE, "noticias", RECEPCION_CANARIAS, "Solo recepción", "");
        var editionId = newsletter.compose(PUBLISHER, "Boletín", "Hola", List.of(rc), Audience.EVERYONE);
        newsletter.schedule(PUBLISHER, editionId, clock.instant());
        assertThat(newsletter.sendDue()).isEqualTo(2); // ana (es) and jonas (de): same hash, two languages; klaus nothing
        assertThat(mails).hasSize(2);
        assertThat(editions.find(editionId).orElseThrow().status().name()).isEqualTo("SENT");
        assertThat(newsletter.sendDue()).isZero();
    }

    @Test
    void reconfirmation_resets_who_has_confirmed() {
        var id = publish(ItemType.NOTICE, "noticias", Audience.EVERYONE, Map.of("es", new Translation("Política", "", "")),
                List.of(), null, null, true);
        var ana = reader("ana", ANA);
        assertThat(readerActions.pendingConfirmations(ana)).extracting(VisibleItem::itemId).containsExactly(id);
        readerActions.confirm(ana, id);
        assertThat(readerActions.pendingConfirmations(ana)).isEmpty();
        itemCommands.edit(PUBLISHER, id, new ItemContent("noticias", null, Map.of("es", new Translation("Política v2", "", "")),
                List.of(), null, null, false, true, null, null));
        itemCommands.publish(PUBLISHER, id, false);
        assertThat(readerActions.pendingConfirmations(ana)).as("without reconfirmation").isEmpty();
        itemCommands.edit(PUBLISHER, id, new ItemContent("noticias", null, Map.of("es", new Translation("Política v3", "", "")),
                List.of(), null, null, false, true, null, null));
        itemCommands.publish(PUBLISHER, id, true);
        assertThat(readerActions.pendingConfirmations(ana)).as("with reconfirmation").hasSize(1);
    }

    @Test
    void an_editor_without_publisher_role_can_submit_but_not_publish() {
        var id = itemCommands.create(EDITOR, ItemType.ARTICLE, "es", new ItemContent("noticias", null,
                Map.of("es", new Translation("Borrador", "", "")), List.of(), null, null, false, false, null, null));
        itemCommands.submitForReview(EDITOR, id);
        assertThatThrownBy(() -> itemCommands.publish(EDITOR, id, false)).isInstanceOf(SecurityException.class);
    }

    @Test
    void an_unknown_hash_sees_the_right_thing_and_is_materialised() {
        var id = publish(ItemType.ARTICLE, "noticias", Audience.in(Attribute.HOTEL, "CUN01"), "Cancún", "");
        var newcomer = profile("CUN01", "QUINTANA_ROO", "PISOS", "es");
        assertThat(jdbc.queryForObject("select count(*) from ch_profile where hash = ?", Long.class, newcomer.hash())).isZero();
        assertThat(visibility.isVisible(newcomer, id)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from ch_profile where hash = ?", Long.class, newcomer.hash())).isOne();
        assertThat(visibility.isVisible(ANA, id)).isFalse();
    }

    @Test
    void the_projection_rebuilt_from_scratch_is_the_direct_evaluation() {
        publish(ItemType.ARTICLE, "noticias", RECEPCION_CANARIAS, "A", "");
        publish(ItemType.ARTICLE, "noticias", Audience.anyOf(Audience.in(Attribute.HOTEL, "PMI01"),
                Audience.in(Attribute.DEPARTMENT, "RECEPCION")), "B", "");
        var before = jdbc.queryForList("select item_id, hashes::text from ch_item_doc order by item_id");
        jdbc.update("update ch_item_doc set hashes = '{}'");
        projection.rebuild();
        assertThat(jdbc.queryForList("select item_id, hashes::text from ch_item_doc order by item_id")).isEqualTo(before);
    }

    static float[] vector(String text) {
        var v = new float[384];
        for (var word : text.toLowerCase().replace("query: ", "").replace("passage: ", "").split("[^\\p{L}]+")) {
            if (!word.isBlank()) {
                v[Math.floorMod(word.hashCode(), 384)] += 1;
            }
        }
        return v;
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
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
