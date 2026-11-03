package io.mateu.ecdemo1.contenthub.infra;

import io.mateu.ecdemo1.contenthub.application.directory.DirectoryUpdates;
import io.mateu.ecdemo1.contenthub.application.directory.EmployeeMessage;
import io.mateu.ecdemo1.contenthub.application.events.DomainEvents;
import io.mateu.ecdemo1.contenthub.application.events.ProjectionListener;
import io.mateu.ecdemo1.contenthub.application.out.Mailer;
import io.mateu.ecdemo1.contenthub.application.out.NewsletterPreferences;
import io.mateu.ecdemo1.contenthub.application.out.NewsletterTracking;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.ItemCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.NavigationCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.NewsletterEditorial;
import io.mateu.ecdemo1.contenthub.application.usecases.NewsletterRenderer;
import io.mateu.ecdemo1.contenthub.application.usecases.NewsletterService;
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
import io.mateu.ecdemo1.contenthub.domain.newsletter.Cadence;
import io.mateu.ecdemo1.contenthub.domain.newsletter.Newsletter;
import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterIssue;
import io.mateu.ecdemo1.contenthub.domain.shared.DomainEvent;
import io.mateu.ecdemo1.contenthub.domain.site.Site;
import io.mateu.ecdemo1.contenthub.infra.in.mcp.NewsletterMcpTools;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.NewsletterMigration;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgContentIndex;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgEmployeeDirectory;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgHomePages;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgItemRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgNamedAudienceRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgNavigationRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgNewsletterIssueRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgNewsletterPreferences;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgNewsletterRepository;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgNewsletterTracking;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.PgSiteRepository;
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

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Newsletters as sites, over a real PostgreSQL: the scheduler opening drafts (once per slot), what an issue
 * gets by itself, sending (one email per group, SENT when every group went), the subscription per newsletter,
 * the web version for a reader, and the migration of the editions of before.
 */
class NewslettersTest {

    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static JdbcTemplate jdbc;
    static DriverManagerDataSource ds;

    static final Reader PUBLISHER = new Reader("pablo", "Pablo", EmployeeAttributes.NONE,
            EnumSet.allOf(EditorialPermission.class), null);
    static final Reader EDITOR = new Reader("eva", "Eva", EmployeeAttributes.NONE, EnumSet.of(EditorialPermission.EDIT), null);
    static final Audience RECEPCION = Audience.in(Attribute.DEPARTMENT, "RECEPCION");
    /** Monday 2 November 2026, 10:00 in Madrid. */
    static final Instant MONDAY_10 = Instant.parse("2026-11-02T09:00:00Z");

    ConsumersTest.MutableClock clock;
    Visibility visibility;
    ItemCommands items;
    NavigationCommands navigation;
    NewsletterEditorial editorial;
    NewsletterService service;
    PgNewsletterIssueRepository issues;
    PgNewsletterPreferences preferences;
    DirectoryUpdates directory;
    final List<String> mails = new ArrayList<>();
    boolean redirected;

    @BeforeAll
    static void start() {
        PG.start();
        ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
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
        for (var t : List.of("ch_item", "ch_navigation", "ch_named_audience", "ch_newsletter", "ch_newsletter_pref",
                "ch_newsletter_event", "ch_employee", "ch_profile", "ch_item_doc", "ch_block_doc", "ch_item_media",
                "ch_projection_state", "ch_chunk", "ch_site", "ch_nl_newsletter", "ch_nl_issue", "ch_nl_subscription",
                "ch_home_page", "ch_site_home_page")) {
            jdbc.update("delete from " + t);
        }
        mails.clear();
        redirected = false;
        clock = new ConsumersTest.MutableClock(MONDAY_10);
        var itemRepo = new PgItemRepository(jdbc);
        var employees = new PgEmployeeDirectory(jdbc);
        var index = new PgContentIndex(jdbc);
        var named = new NamedAudiences(new PgNamedAudienceRepository(jdbc));
        var cache = new ProjectionCache();
        var projection = new Projection(index, itemRepo, new PgSlotRepository(jdbc), employees, named, cache);
        var listener = new ProjectionListener(projection, cache);
        var events = new DomainEvents(e -> { }, e -> listener.on((DomainEvent) e));
        visibility = new Visibility(index, projection, clock);
        items = new ItemCommands(itemRepo, events, employees, named, clock);
        var nav = new PgNavigationRepository(jdbc);
        navigation = new NavigationCommands(nav, events);
        directory = new DirectoryUpdates(employees, projection, events);
        var sites = new PgSiteRepository(jdbc);
        issues = new PgNewsletterIssueRepository(jdbc);
        preferences = new PgNewsletterPreferences(jdbc);
        editorial = new NewsletterEditorial(sites, new PgNewsletterRepository(jdbc), issues, nav, itemRepo,
                new PgHomePages(jdbc), employees, named, visibility, items, clock);
        service = new NewsletterService(issues, sites, nav, employees, visibility, named, new Mailer() {
            @Override
            public void send(List<String> to, String subject, String html) {
                mails.add(subject + " → " + to + "\n" + html);
            }

            @Override
            public boolean redirected() {
                return redirected;
            }
        }, clock, preferences, itemRepo);

        employee("ana", "TFS01", "RECEPCION", "es");
        employee("jonas", "TFS01", "RECEPCION", "de");
        employee("klaus", "PMI01", "COCINA", "de");
        navigation.add(PUBLISHER, "noticias", null, Map.of("es", "Noticias"));
        navigation.add(PUBLISHER, "rrhh", null, Map.of("es", "RRHH"));
        navigation.add(PUBLISHER, "nominas", "rrhh", Map.of("es", "Nóminas"));
    }

    void employee(String id, String hotel, String department, String language) {
        directory.apply(new EmployeeMessage(id + "-" + System.nanoTime(), EmployeeMessage.UPSERTED, id, id,
                id + "@example.com", true, null, null, Map.of("hotel", hotel, "department", department, "language", language),
                null, null));
    }

    String publish(String site, String section, Audience audience, String title) {
        var content = new ItemContent(section, audience, Map.of("es", new Translation(title, "Resumen " + title, "<p>x</p>")),
                List.of(), null, null, false, false, null, null);
        var id = site == null ? items.create(EDITOR, ItemType.ARTICLE, "es", content)
                : items.createInSite(EDITOR, site, ItemType.ARTICLE, "es", content);
        items.submitForReview(EDITOR, id);
        items.publish(PUBLISHER, id, false);
        return id;
    }

    /** «Boletín»: weekly on Mondays at 9; «Editorial» (its own) and «Noticias» (the intranet's news and RRHH). */
    String bulletin(boolean autoSend, Audience audience) {
        return editorial.create(PUBLISHER, new Site.Definition("Boletín", "Cada lunes", audience, List.of("es"), null,
                        null, "<p>Cabecera</p>", "<p>Pie</p>", true),
                List.of(new NewsletterEditorial.SectionSpec("editorial", "Editorial", List.of()),
                        new NewsletterEditorial.SectionSpec("noticias", "Noticias", List.of("noticias", "rrhh"))),
                new Newsletter.Settings(List.of(), Map.of(), Cadence.weekly(DayOfWeek.MONDAY, LocalTime.of(9, 0)), autoSend));
    }

    @Test
    void a_newsletter_is_a_site_of_its_own() {
        var id = bulletin(false, Audience.EVERYONE);
        assertThat(new PgSiteRepository(jdbc).find(id).orElseThrow().kind()).isEqualTo(Site.Kind.NEWSLETTER);
        assertThat(new PgNavigationRepository(jdbc).get(id).sections()).extracting(s -> s.id())
                .containsExactly("editorial", "noticias");
        // the intranet's tree is not touched
        assertThat(new PgNavigationRepository(jdbc).get().find("editorial")).isEmpty();
        // its own article: in its site, not in the intranet
        var own = publish(id, "editorial", Audience.EVERYONE, "La carta del director");
        assertThat(visibility.visibles(ConsumersTest.ANA, io.mateu.ecdemo1.contenthub.application.out.ContentQuery.latest(20))
                .content()).extracting(i -> i.itemId()).doesNotContain(own);
        // a layout made of its sections
        assertThat(editorial.newsletter(id).settings().layout()).extracting(Block::type)
                .containsExactly(BlockType.HTML, BlockType.NEWSLETTER_SECTION, BlockType.NEWSLETTER_SECTION);
        // sending without review is publishing
        assertThatThrownBy(() -> editorial.create(EDITOR, new Site.Definition("Otro", "", null, List.of(), null, null,
                null, null, true), List.of(), new Newsletter.Settings(List.of(), Map.of(), Cadence.NONE, true)))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void the_scheduler_opens_one_draft_per_slot_prefilled_with_what_was_published_since() {
        var id = bulletin(false, Audience.EVERYONE);
        clock.advance(Duration.ofHours(1));
        var news = publish(null, "noticias", Audience.EVERYONE, "Noticia");
        var payroll = publish(null, "nominas", Audience.EVERYONE, "Nóminas");
        var kitchen = publish(null, "noticias", Audience.in(Attribute.DEPARTMENT, "LAVANDERIA"), "Nadie la ve");
        var own = publish(id, "editorial", Audience.EVERYONE, "Carta");
        // nothing due the day it was created
        assertThat(editorial.createDue(clock.instant())).isEmpty();
        clock.advance(Duration.ofDays(7).minusHours(1));
        var opened = editorial.createDue(clock.instant());
        assertThat(opened).containsExactly(id + "-20261109-0900");
        var issue = issues.find(opened.getFirst()).orElseThrow();
        assertThat(issue.number()).isEqualTo(1);
        assertThat(issue.status()).isEqualTo(NewsletterIssue.Status.DRAFT);
        assertThat(issue.section("noticias")).containsExactlyInAnyOrder(news, payroll).doesNotContain(kitchen);
        assertThat(issue.section("editorial")).containsExactly(own);
        // idempotent: the same slot never opens another, even asked again
        assertThat(editorial.createDue(clock.instant().plusSeconds(30))).isEmpty();
        assertThat(issues.ofNewsletter(id)).hasSize(1);
        // the next issue: only what came after this one was opened
        clock.advance(Duration.ofHours(1));
        var later = publish(null, "noticias", Audience.EVERYONE, "Más tarde");
        clock.advance(Duration.ofDays(7));
        var second = issues.find(editorial.createDue(clock.instant()).getFirst()).orElseThrow();
        assertThat(second.number()).isEqualTo(2);
        assertThat(second.itemIds()).containsExactly(later);
    }

    @Test
    void a_newsletter_that_sends_without_review_schedules_its_issue() {
        var id = bulletin(true, Audience.EVERYONE);
        publish(null, "noticias", Audience.EVERYONE, "Noticia");
        clock.advance(Duration.ofDays(7));
        var issue = issues.find(editorial.createDue(clock.instant()).getFirst()).orElseThrow();
        assertThat(issue.status()).isEqualTo(NewsletterIssue.Status.SCHEDULED);
        assertThat(service.sendDue()).isEqualTo(2); // ana and jonas (her profile; written only in es, so es), klaus
        assertThat(issues.find(issue.id()).orElseThrow().status()).isEqualTo(NewsletterIssue.Status.SENT);
        assertThat(id).isNotBlank();
    }

    @Test
    void sending_goes_once_per_group_with_what_each_sees_and_marks_it_sent() {
        var id = bulletin(false, Audience.EVERYONE);
        var reception = publish(null, "noticias", RECEPCION, "Solo recepción");
        var issueId = editorial.newIssue(EDITOR, id);
        var issue = issues.find(issueId).orElseThrow();
        assertThat(issue.section("noticias")).containsExactly(reception);
        // a block only for the kitchen
        var blocks = new ArrayList<>(issue.blocks());
        blocks.add(new Block("cocina", BlockType.HTML, Audience.in(Attribute.DEPARTMENT, "COCINA"), "Para cocina",
                "<p>Menú [[media:m1]]</p>", null, null, null, null, null));
        editorial.editIssue(EDITOR, issueId, "Boletín nº 1", issue.date(), blocks, issue.sections());
        assertThatThrownBy(() -> editorial.schedule(EDITOR, issueId, null)).isInstanceOf(SecurityException.class);
        editorial.schedule(PUBLISHER, issueId, clock.instant());

        // written only in Spanish: jonas (de) reads it in es, with ana — one group; klaus another
        assertThat(service.sendDue()).isEqualTo(2);
        var toAna = mails.stream().filter(m -> m.contains("ana@example.com")).findFirst().orElseThrow();
        assertThat(toAna).startsWith("Boletín nº 1").contains("jonas@example.com").contains("Solo recepción").doesNotContain("Para cocina")
                .contains("/intranet/n/" + id + "/numeros/" + issueId) // ver en el navegador
                .contains("/intranet/n/" + id + "\"") // unsubscribe, per newsletter
                .contains("Cabecera").contains("Pie");
        var toKlaus = mails.stream().filter(m -> m.contains("klaus@example.com")).findFirst().orElseThrow();
        assertThat(toKlaus).doesNotContain("Solo recepción").contains("Para cocina")
                .doesNotContain("[[media:").contains("[ver en la versión web]");
        var sent = issues.find(issueId).orElseThrow();
        assertThat(sent.status()).isEqualTo(NewsletterIssue.Status.SENT);
        assertThat(sent.sentAt()).isEqualTo(clock.instant());
        assertThat(sent.sends()).hasSize(2);
        assertThat(service.sendDue()).isZero();
        // and the archive shows it, for whom the newsletter is
        assertThat(service.archive(id)).extracting(NewsletterIssue::id).containsExactly(issueId);
    }

    @Test
    void unsubscribing_from_one_newsletter_leaves_the_others() {
        var a = bulletin(false, Audience.EVERYONE);
        var b = editorial.create(PUBLISHER, new Site.Definition("Recepción al día", "", RECEPCION, List.of(), null, null,
                        null, null, true), List.of(new NewsletterEditorial.SectionSpec("noticias", "Noticias", List.of("noticias"))),
                new Newsletter.Settings(List.of(), Map.of(), Cadence.NONE, false));
        publish(null, "noticias", Audience.EVERYONE, "Para todos");
        var ana = new Reader("ana", "Ana", ConsumersTest.ANA, Set.of(), null);
        // Ana is in both audiences; Klaus only in the first
        assertThat(service.newslettersOf(ana)).extracting(s -> s.site().id()).containsExactlyInAnyOrder(a, b);
        assertThat(service.newslettersOf(new Reader("klaus", "Klaus", ConsumersTest.KLAUS, Set.of(), null)))
                .extracting(s -> s.site().id()).containsExactly(a);
        service.subscribe(ana, a, false, Set.of());
        var issueA = editorial.newIssue(EDITOR, a);
        var issueB = editorial.newIssue(EDITOR, b);
        assertThat(service.groups(issues.find(issueA).orElseThrow())).noneMatch(g -> g.recipients().stream()
                .anyMatch(e -> e.userId().equals("ana")));
        assertThat(service.groups(issues.find(issueB).orElseThrow())).anyMatch(g -> g.recipients().stream()
                .anyMatch(e -> e.userId().equals("ana")));
        // only some sections of a newsletter
        service.subscribe(ana, b, true, Set.of("noticias", "inventada"));
        assertThat(preferences.of("ana", b).sections()).containsExactly("noticias");
        // viewing as another profile: nothing is touched
        assertThatThrownBy(() -> service.subscribe(new Reader("ana", "Ana", ConsumersTest.ANA, Set.of(), "h1"), a, true, Set.of()))
                .isInstanceOf(IllegalStateException.class);
        // a newsletter that is not for you
        assertThatThrownBy(() -> service.subscribe(new Reader("klaus", "Klaus", ConsumersTest.KLAUS, Set.of(), null), b, true,
                Set.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void the_web_version_is_the_issue_with_the_readers_visibility() {
        var id = bulletin(false, Audience.EVERYONE);
        var reception = publish(null, "noticias", RECEPCION, "Solo recepción");
        var issueId = editorial.newIssue(EDITOR, id);
        var ana = new Reader("ana", "Ana", ConsumersTest.ANA, Set.of(), null);
        // not sent yet: not in the archive, not readable
        assertThat(service.readableIssue(ana, id, issueId)).isEmpty();
        editorial.schedule(PUBLISHER, issueId, clock.instant());
        service.sendDue();
        var issue = service.readableIssue(ana, id, issueId).orElseThrow();
        var links = new NewsletterRenderer.Links() {
            public String item(io.mateu.ecdemo1.contenthub.application.out.VisibleItem item) {
                return "/intranet/noticias/" + item.itemId();
            }

            public String rich(String html) {
                return html;
            }

            public String medium(String mediaId) {
                return "<video>" + mediaId + "</video>";
            }

            public String permalink() {
                return "/p";
            }

            public String unsubscribe() {
                return "/u";
            }

            public String logo() {
                return null;
            }
        };
        assertThat(service.render(issue, ConsumersTest.ANA, null, NewsletterRenderer.Mode.WEB, "Hola, Ana", links))
                .contains("Solo recepción").contains("/intranet/noticias/" + reception);
        assertThat(service.render(issue, ConsumersTest.KLAUS, null, NewsletterRenderer.Mode.WEB, "Hola, Klaus", links))
                .doesNotContain("Solo recepción");
    }

    /** The MCP tools (desktop AI clients, the chat agent): what the portal shows the caller, sent issues only. */
    @Test
    void the_mcp_tools_give_the_caller_their_newsletters_and_the_sent_issues_as_they_get_them() {
        var id = bulletin(false, Audience.EVERYONE);
        var reception = editorial.create(PUBLISHER, new Site.Definition("Recepción al día", "", RECEPCION, List.of(), null,
                        null, null, null, true), List.of(new NewsletterEditorial.SectionSpec("noticias", "Noticias", List.of("noticias"))),
                new Newsletter.Settings(List.of(), Map.of(), Cadence.NONE, false));
        var onlyReception = publish(null, "noticias", RECEPCION, "Solo recepción");
        var forAll = publish(null, "noticias", Audience.EVERYONE, "Para todos");
        var ana = new Reader("ana", "Ana", ConsumersTest.ANA, Set.of(), null);
        var klaus = new Reader("klaus", "Klaus", ConsumersTest.KLAUS, Set.of(), null);
        var anasTools = new NewsletterMcpTools(service, () -> ana);
        var klausTools = new NewsletterMcpTools(service, () -> klaus);

        // the newsletters whose audience admits them, with their subscription
        service.subscribe(ana, reception, false, Set.of());
        assertThat(anasTools.mis_newsletters()).extracting(NewsletterMcpTools.NewsletterSummary::id)
                .containsExactlyInAnyOrder(id, reception);
        assertThat(anasTools.mis_newsletters()).filteredOn(n -> n.id().equals(reception)).singleElement()
                .satisfies(n -> assertThat(n.subscribed()).isFalse());
        assertThat(klausTools.mis_newsletters()).extracting(NewsletterMcpTools.NewsletterSummary::id).containsExactly(id);

        // a draft is nobody's here, not even an editor's
        var issueId = editorial.newIssue(EDITOR, id);
        var editor = new Reader("ana", "Ana", ConsumersTest.ANA, EnumSet.allOf(EditorialPermission.class), null);
        assertThat(new NewsletterMcpTools(service, () -> editor).numeros_newsletter(id)).isEmpty();
        assertThat(anasTools.leer_numero(id, issueId).blocks()).isEmpty();
        assertThat(anasTools.leer_numero(id, issueId).subject()).startsWith("No existe");
        assertThat(anasTools.mis_newsletters()).filteredOn(n -> n.id().equals(id)).singleElement()
                .satisfies(n -> assertThat(n.latestIssue()).isNull());

        editorial.schedule(PUBLISHER, issueId, clock.instant());
        service.sendDue();

        assertThat(anasTools.numeros_newsletter(id)).singleElement().satisfies(i -> {
            assertThat(i.id()).isEqualTo(issueId);
            assertThat(i.number()).isEqualTo(1);
            assertThat(i.link()).endsWith("/intranet/n/" + id + "/numeros/" + issueId);
        });
        assertThat(anasTools.mis_newsletters()).filteredOn(n -> n.id().equals(id)).singleElement()
                .satisfies(n -> assertThat(n.latestIssue().id()).isEqualTo(issueId));

        // the issue as each gets it: their items, absolute links
        var forAna = anasTools.leer_numero(id, issueId);
        assertThat(forAna.number()).isEqualTo(1);
        assertThat(forAna.link()).startsWith("http").endsWith("/intranet/n/" + id + "/numeros/" + issueId);
        var anasItems = forAna.blocks().stream().flatMap(b -> b.items().stream()).toList();
        assertThat(anasItems).extracting(NewsletterMcpTools.IssueItem::id).contains(onlyReception, forAll);
        assertThat(anasItems).extracting(NewsletterMcpTools.IssueItem::link).allMatch(l -> l.startsWith("http"));
        assertThat(forAna.blocks()).filteredOn(b -> !b.items().isEmpty()).extracting(NewsletterMcpTools.IssueBlock::title)
                .contains("Noticias");
        var klausItems = klausTools.leer_numero(id, issueId).blocks().stream().flatMap(b -> b.items().stream()).toList();
        assertThat(klausItems).extracting(NewsletterMcpTools.IssueItem::id).contains(forAll).doesNotContain(onlyReception);
        // empty: the latest sent
        assertThat(anasTools.leer_numero(id, " ").id()).isEqualTo(issueId);

        // a newsletter that is not for them: nothing of it, whatever the id
        assertThat(klausTools.numeros_newsletter(reception)).isEmpty();
        assertThat(klausTools.leer_numero(reception, null).blocks()).isEmpty();
        assertThat(klausTools.leer_numero(id + "-otra", issueId).blocks()).isEmpty();
    }

    @Test
    void the_test_send_goes_to_the_editor_and_records_nothing() {
        var id = bulletin(false, Audience.EVERYONE);
        publish(null, "noticias", Audience.EVERYONE, "Noticia");
        var issueId = editorial.newIssue(EDITOR, id);
        var ana = new Reader("ana", "Ana", ConsumersTest.ANA, Set.of(EditorialPermission.EDIT), null);
        assertThat(service.sendTest(ana, issueId)).isEqualTo("ana@example.com");
        assertThat(mails).singleElement().asString().startsWith("[Prueba] ").contains("[ana@example.com]");
        assertThat(issues.find(issueId).orElseThrow().sends()).isEmpty();
    }

    @Test
    void redirected_an_issue_goes_as_one_digest_with_a_part_per_group() {
        redirected = true;
        var id = bulletin(false, Audience.EVERYONE);
        publish(null, "noticias", Audience.EVERYONE, "Noticia");
        var issueId = editorial.newIssue(EDITOR, id);
        editorial.schedule(PUBLISHER, issueId, clock.instant());
        assertThat(service.sendDue()).isEqualTo(1);
        assertThat(mails).singleElement().asString().contains("[2 grupos]").contains("Grupo 2 de 2");
        assertThat(issues.find(issueId).orElseThrow().status()).isEqualTo(NewsletterIssue.Status.SENT);
    }

    /** Its menu is the configurable one (owned by its site), seeded with its home, sections, archive and pages. */
    @Test
    void a_newsletters_menu_is_its_sites_configurable_menu() {
        jdbc.update("delete from ch_menu");
        var itemRepo = new PgItemRepository(jdbc);
        var sites = new PgSiteRepository(jdbc);
        var nav = new PgNavigationRepository(jdbc);
        var routes = new io.mateu.ecdemo1.contenthub.infra.config.SiteAwareRoutes();
        var store = new io.mateu.ecdemo1.contenthub.infra.out.persistence.PgMenuRepository(jdbc);
        var siteMenus = new io.mateu.ecdemo1.contenthub.application.menu.SiteMenus(store,
                new NamedAudiences(new PgNamedAudienceRepository(jdbc)), List.of(
                new io.mateu.ecdemo1.contenthub.application.menu.StandardTargets.Pages(visibility, itemRepo, routes),
                new io.mateu.ecdemo1.contenthub.application.menu.StandardTargets.Sections(visibility, nav, routes),
                new io.mateu.ecdemo1.contenthub.application.menu.NewsletterTargets.Home(sites, routes),
                new io.mateu.ecdemo1.contenthub.application.menu.NewsletterTargets.Archive(sites, routes),
                new io.mateu.ecdemo1.contenthub.application.menu.NewsletterTargets.Issue(sites, issues, routes)));
        var newsletterMenus = new io.mateu.ecdemo1.contenthub.application.menu.NewsletterMenus(siteMenus, store, sites, nav, itemRepo);
        editorial.withMenus(newsletterMenus);
        var id = bulletin(false, Audience.EVERYONE);
        var own = publish(id, "editorial", Audience.EVERYONE, "Carta");
        var page = editorial.newPage(EDITOR, id, "Quiénes somos");
        items.submitForReview(EDITOR, page);
        items.publish(PUBLISHER, page, false);

        var ana = new Reader("ana", "Ana", ConsumersTest.ANA, Set.of(), null);
        var shown = siteMenus.shown(new io.mateu.ecdemo1.contenthub.application.menu.MenuContext(id, ana, Set.of()));
        // home, its sections with something visible (only Editorial so far), archive, its page — under its routes
        assertThat(shown).extracting(e -> ((io.mateu.ecdemo1.contenthub.application.menu.MenuLink.Route) e.link()).path())
                .containsExactly("/intranet/n/" + id, "/intranet/n/" + id + "/secciones/editorial",
                        "/intranet/n/" + id + "/archivo", "/intranet/n/" + id + "/p/" + page);
        assertThat(siteMenus.sites()).contains(id);
        // newsletter kinds apply to newsletter sites only
        assertThat(siteMenus.targetKinds(id)).extracting(r -> r.kind()).contains("ARCHIVE", "ISSUE", "NEWSLETTER_HOME");
        assertThat(siteMenus.targetKinds("intranet")).extracting(r -> r.kind()).doesNotContain("ARCHIVE", "ISSUE");
        // seeding again changes nothing
        assertThat(newsletterMenus.seedIfAbsent(id)).isFalse();
        assertThat(own).isNotBlank();
        editorial.withMenus(null);
    }

    @Test
    void an_item_with_a_block_of_an_unknown_type_is_read_without_it() {
        var id = publish(null, "noticias", Audience.EVERYONE, "Con bloque raro");
        jdbc.update("update ch_item set doc = jsonb_set(doc, '{draft,blocks}', "
                + "'[{\"id\":\"r1\",\"type\":\"FROM_A_NEWER_VERSION\"},{\"id\":\"h1\",\"type\":\"HTML\",\"html\":\"<p>x</p>\"}]'::jsonb) where id = ?", id);
        var item = new PgItemRepository(jdbc).find(id).orElseThrow();
        assertThat(item.draft().blocks()).extracting(Block::id).containsExactly("h1");
        assertThat(new PgItemRepository(jdbc).findAll()).extracting(i -> i.id()).contains(id);
    }

    @Test
    void the_editions_of_before_become_issues_of_the_bulletin_once() {
        // two editions as they were stored, one sent with its opens, and two preferences
        jdbc.update("insert into ch_newsletter (id, doc, status, scheduled_at) values ('w-2026-W44', ?::jsonb, 'SENT', ?)",
                "{\"id\":\"w-2026-W44\",\"title\":\"Boletín de la semana 44\",\"intro\":\"<p>Hola</p>\",\"itemIds\":[\"a\",\"b\"],"
                        + "\"audience\":{\"op\":\"in\",\"attribute\":\"DEPARTMENT\",\"values\":[\"RECEPCION\"]},\"status\":\"SENT\","
                        + "\"sends\":{\"h|es\":{\"hash\":\"h\",\"language\":\"es\",\"recipients\":2,\"itemIds\":[\"a\"],"
                        + "\"sentAt\":\"2026-10-26T08:01:00Z\",\"error\":null}}}",
                java.sql.Timestamp.from(Instant.parse("2026-10-26T08:00:00Z")));
        jdbc.update("insert into ch_newsletter (id, doc, status, scheduled_at) values ('n1', ?::jsonb, 'DRAFT', null)",
                "{\"id\":\"n1\",\"title\":\"Especial\",\"intro\":\"Texto\",\"itemIds\":[\"c\"],\"audience\":{\"op\":\"everyone\"},"
                        + "\"status\":\"DRAFT\",\"sends\":{}}");
        jdbc.update("insert into ch_newsletter_event (edition_id, grp, kind, item_id, at) values ('w-2026-W44', 'h|es', 'OPEN', null, now())");
        jdbc.update("insert into ch_newsletter_pref (user_id, subscribed, sections) values ('klaus', false, '{}'), ('ana', true, '{noticias}')");

        var migration = new NewsletterMigration(jdbc, editorial, new PgNewsletterRepository(jdbc), issues,
                new PgNavigationRepository(jdbc), new TransactionTemplate(new DataSourceTransactionManager(ds)), clock, true, false);
        var r = migration.migrate();
        assertThat(r.issues()).isEqualTo(2);
        assertThat(r.subscriptions()).isEqualTo(2);

        var bulletin = issues.ofNewsletter(NewsletterMigration.BULLETIN);
        assertThat(bulletin).extracting(NewsletterIssue::id).containsExactly("n1", "w-2026-W44"); // numbered by date
        var sent = issues.find("w-2026-W44").orElseThrow();
        assertThat(sent.number()).isEqualTo(1);
        assertThat(sent.status()).isEqualTo(NewsletterIssue.Status.SENT);
        assertThat(sent.sentAt()).isEqualTo(Instant.parse("2026-10-26T08:01:00Z"));
        assertThat(sent.section(NewsletterMigration.HIGHLIGHTS)).containsExactly("a", "b");
        assertThat(sent.slot()).isEqualTo(Instant.parse("2026-10-26T08:00:00Z")); // its week's Monday at 9
        assertThat(sent.sends()).hasSize(1);
        assertThat(sent.blocks()).extracting(Block::type).containsExactly(BlockType.HTML, BlockType.NEWSLETTER_SECTION);
        assertThat(issues.find("n1").orElseThrow().number()).isEqualTo(2);
        // the opens stay with it
        assertThat(new PgNewsletterTracking(jdbc).totals(List.of("w-2026-W44")).get("w-2026-W44"))
                .isEqualTo(new NewsletterTracking.Totals(1, 0));
        assertThat(new PgNewsletterTracking(jdbc).recentEditions(5)).extracting(NewsletterTracking.EditionStats::title)
                .containsExactly("Boletín de la semana 44");
        // the site: «Boletín de la intranet», the latest edition's audience, weekly
        var site = new PgSiteRepository(jdbc).find(NewsletterMigration.BULLETIN).orElseThrow();
        assertThat(site.name()).isEqualTo("Boletín de la intranet");
        assertThat(site.audience()).isEqualTo(Audience.EVERYONE);
        assertThat(editorial.newsletter(NewsletterMigration.BULLETIN).settings().cadence().frequency())
                .isEqualTo(Cadence.Frequency.WEEKLY);
        // the subscriptions
        assertThat(preferences.of("klaus", NewsletterMigration.BULLETIN).subscribed()).isFalse();
        assertThat(preferences.of("ana", NewsletterMigration.BULLETIN)).isEqualTo(NewsletterPreferences.Preference.DEFAULT);

        // again: nothing more
        var again = migration.migrate();
        assertThat(again.issues()).isZero();
        assertThat(again.subscriptions()).isZero();
        assertThat(issues.ofNewsletter(NewsletterMigration.BULLETIN)).hasSize(2);
        // the next number continues
        assertThat(issues.find(editorial.newIssue(EDITOR, NewsletterMigration.BULLETIN)).orElseThrow().number()).isEqualTo(3);
    }
}
