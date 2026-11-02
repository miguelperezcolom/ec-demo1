package io.mateu.ecdemo1.contenthub.application.health;

import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.health.Finding;
import io.mateu.ecdemo1.contenthub.domain.health.Finding.Fix;
import io.mateu.ecdemo1.contenthub.domain.health.Finding.Subject;
import io.mateu.ecdemo1.contenthub.domain.health.HealthCheck;
import io.mateu.ecdemo1.contenthub.domain.health.LinkStatus;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterIssue;
import io.mateu.ecdemo1.contenthub.domain.uisource.RemoteUi;
import io.mateu.ecdemo1.contenthub.application.menu.NewsletterTargets;
import java.time.LocalDate;
import io.mateu.ecdemo1.contenthub.domain.media.MediaAsset;
import io.mateu.ecdemo1.contenthub.domain.menu.MenuTarget;
import io.mateu.ecdemo1.contenthub.domain.menu.SiteMenu;
import io.mateu.ecdemo1.contenthub.domain.slot.Slot;
import io.mateu.ecdemo1.contenthub.domain.slot.SlotVariant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.mateu.ecdemo1.contenthub.application.health.HealthFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Each check's rules, on content built through the domain, with no database and no network. */
class HealthChecksTest {

    static final HealthSettings SETTINGS = HealthSettings.defaults();

    static List<Finding> of(HealthCheck check, ContentSnapshot s) {
        return of(check, s, Map.of());
    }

    static List<Finding> of(HealthCheck check, ContentSnapshot s, Map<String, LinkStatus> external) {
        return HealthChecks.all(s, external, SETTINGS).stream().filter(f -> f.check() == check).toList();
    }

    static Block block(String id, BlockType type, String html, String itemId, String sectionId, List<Block.Link> links,
                       String mediaId) {
        return new Block(id, type, Audience.EVERYONE, "Bloque " + id, html, itemId, sectionId, 5, links, mediaId);
    }

    @Nested
    class BrokenLinks {

        @Test
        void internal_links_to_items_that_are_missing_unpublished_or_retired_are_broken_and_good_ones_are_not() {
            var retired = published("viejo", "Viejo", "");
            retired.retire(NOW);
            var s = snapshot()
                    .item(published("ok", "Bueno", ""))
                    .item(draft("borrador", "Borrador", ""))
                    .item(retired)
                    .item(published("a", "Con enlaces", "<p><a href=\"/intranet/noticias/ok\">ok</a>"
                            + "<a href=\"http://ec1.localhost:8800/intranet/noticias/nadie\">x</a>"
                            + "<a href=\"/_intranet/intranet/noticias/borrador\">y</a>"
                            + "<a href=\"/intranet/noticias/viejo/\">z</a>"
                            + "<a href=\"/intranet/personas/ana.perez\">ana</a>"
                            + "<a href=\"/intranet/personas/fantasma\">?</a>"
                            + "<a href=\"/intranet/buscar\">buscar</a><a href=\"/intranet/inventada\">?</a>"
                            + "<a href=\"/intranet/secciones/s2\">s2</a><a href=\"/intranet/encuestas/enc9\">?</a>"
                            + "<a href=\"mailto:rrhh@riu.com\">m</a><a href=\"#arriba\">a</a></p>"))
                    .build();

            var problems = of(HealthCheck.BROKEN_LINKS, s).stream().map(Finding::problem).toList();

            assertThat(problems).hasSize(6);
            assertThat(problems).anyMatch(p -> p.contains("el contenido «nadie» no existe"))
                    .anyMatch(p -> p.contains("«Borrador» no está publicado"))
                    .anyMatch(p -> p.contains("«Viejo» está retirado"))
                    .anyMatch(p -> p.contains("no hay nadie con el id «fantasma»"))
                    .anyMatch(p -> p.contains("/intranet/inventada no existe en el portal"))
                    .anyMatch(p -> p.contains("la encuesta «enc9» no existe"));
            assertThat(of(HealthCheck.BROKEN_LINKS, s)).allMatch(f -> f.subject() == Subject.ITEM
                    && f.subjectId().equals("a") && f.fix() == Fix.EDIT_ITEM);
        }

        @Test
        void media_marks_blocks_featured_items_sections_and_quick_links_are_checked_too() {
            var s = snapshot()
                    .media(image("m1", 1000, NOW.minus(Duration.ofDays(10))))
                    .item(published("p", ItemType.PAGE, content("s9", "<p>[[media:m1]] [[media:nada]]</p>", List.of(
                            block("b1", BlockType.MEDIA, null, null, null, List.of(), "otra"),
                            block("b2", BlockType.FEATURED, null, "inexistente", null, List.of(), null),
                            block("b3", BlockType.LATEST, null, null, "s7", List.of(), null),
                            block("b4", BlockType.QUICK_LINKS, null, null, null, List.of(
                                    new Block.Link("Turnos", "/intranet/noticias/perdida"),
                                    new Block.Link("Bueno", "/intranet/noticias/p")), null)), null), NOW.minus(Duration.ofDays(1))))
                    .build();

            var problems = of(HealthCheck.BROKEN_LINKS, s).stream().map(Finding::problem).toList();

            assertThat(problems).hasSize(6)
                    .anyMatch(p -> p.contains("el medio «nada» no existe") && p.contains("cuerpo (es)"))
                    .anyMatch(p -> p.contains("el medio «otra» no existe"))
                    .anyMatch(p -> p.contains("el contenido «inexistente» no existe"))
                    .anyMatch(p -> p.contains("la sección «s7» no existe"))
                    .anyMatch(p -> p.contains("la sección «s9» no existe") && p.contains("su sección"))
                    .anyMatch(p -> p.contains("«perdida» no existe") && p.contains("«Turnos»"));
        }

        @Test
        void a_pasted_signed_media_url_is_a_finding_because_it_expires() {
            var s = snapshot().media(image("m1", 10, NOW.minus(Duration.ofDays(9))))
                    .item(published("a", "A", "<img alt=\"x\" src=\"/_intranet/media/m1?h=EDITOR&e=1&s=abc\"/>"))
                    .build();
            assertThat(of(HealthCheck.BROKEN_LINKS, s)).singleElement()
                    .satisfies(f -> assertThat(f.problem()).contains("URL firmada").contains("[[media:m1]]"));
        }

        @Test
        void an_external_link_is_broken_only_when_the_checker_said_so() {
            var s = snapshot().item(published("a", "A", "<a href=\"https://muerto.example/x\">x</a>"
                    + "<a href=\"https://vivo.example/\">y</a><a href=\"https://pendiente.example/\">z</a>")).build();
            var external = Map.of(
                    "https://muerto.example/x", new LinkStatus("https://muerto.example/x", LinkStatus.Outcome.BROKEN, 404, "no existe (HTTP 404)", NOW),
                    "https://vivo.example/", new LinkStatus("https://vivo.example/", LinkStatus.Outcome.OK, 200, "HTTP 200", NOW));

            assertThat(HealthChecks.externalUrls(s)).containsExactlyInAnyOrder("https://muerto.example/x",
                    "https://vivo.example/", "https://pendiente.example/");
            assertThat(of(HealthCheck.BROKEN_LINKS, s, external)).singleElement()
                    .satisfies(f -> {
                        assertThat(f.target()).isEqualTo("https://muerto.example/x");
                        assertThat(f.problem()).contains("HTTP 404");
                    });
        }

        @Test
        void the_same_url_twice_in_an_item_is_one_finding_that_says_both_places() {
            var s = snapshot().item(published("p", ItemType.PAGE, content("s1", "<a href=\"/intranet/noticias/x\">x</a>",
                    List.of(block("b", BlockType.HTML, "<a href=\"/intranet/noticias/x\">x</a>", null, null, List.of(), null)),
                    null), NOW.minus(Duration.ofDays(1)))).build();
            assertThat(of(HealthCheck.BROKEN_LINKS, s)).singleElement()
                    .satisfies(f -> assertThat(f.problem()).contains("cuerpo (es)").contains("también en bloque html"));
        }

        @Test
        void slots_homes_and_menu_entries_are_checked_and_fixed_in_the_console() {
            var slot = Slot.register("fo.home.banner", "Banner del front office", "");
            slot.putVariant(new SlotVariant("v1", Audience.EVERYONE, 1, "Hola", "<a href=\"/intranet/noticias/x\">x</a>",
                    "/intranet/noticias/y"));
            var menu = SiteMenu.empty(SiteMenu.INTRANET);
            menu.add("e1", null, Map.of("es", "Página"), MenuTarget.page("borrada"), null, true);
            menu.add("e2", null, Map.of("es", "Función"), MenuTarget.builtin("noexiste"), null, true);
            menu.add("e3", null, Map.of("es", "CRS"), MenuTarget.remote("crs", "/bookings"), null, true);
            menu.add("e4", null, Map.of("es", "Retirada"), MenuTarget.remote("vieja", ""), null, true);
            menu.add("e5", null, Map.of("es", "Inactiva"), MenuTarget.page("tampoco"), null, false);
            menu.add("e6", null, Map.of("es", "Sección"), MenuTarget.section("s2"), null, true);
            var b = snapshot();
            b.slots.add(slot);
            b.menus.add(menu);
            b.homes.add("sin-inicio");
            var findings = of(HealthCheck.BROKEN_LINKS, b.build());

            assertThat(findings).allMatch(f -> f.fix() == Fix.CONFIGURE);
            assertThat(findings).filteredOn(f -> f.subject() == Subject.SLOT).hasSize(2);
            assertThat(findings).filteredOn(f -> f.subject() == Subject.HOMES).singleElement()
                    .satisfies(f -> assertThat(f.problem()).contains("«sin-inicio» no existe"));
            assertThat(findings).filteredOn(f -> f.subject() == Subject.MENU).extracting(Finding::subjectId)
                    .containsExactlyInAnyOrder("intranet/e1", "intranet/e2", "intranet/e4");
            assertThat(findings).anyMatch(f -> f.problem().contains("la UI externa «vieja» no está (o ya no está) en el registro"));
            assertThat(findings).allMatch(f -> f.subject() == Subject.SLOT ? f.siteId() == null : "intranet".equals(f.siteId()));
        }

        @Test
        void drafts_and_expired_items_are_not_what_readers_see_so_their_links_are_not_checked() {
            var expired = published("cad", ItemType.ARTICLE, content("s1", "<a href=\"/intranet/noticias/x\">x</a>",
                    List.of(), NOW.minus(Duration.ofDays(1))), NOW.minus(Duration.ofDays(30)));
            var s = snapshot().item(draft("d", "D", "<a href=\"/intranet/noticias/x\">x</a>")).item(expired).build();
            assertThat(of(HealthCheck.BROKEN_LINKS, s)).isEmpty();
        }
    }

    @Nested
    class Sites {

        static Item inNewsletter(String id, ItemType type, ItemContent content) {
            var item = Item.create(id, "boletin", type, "eva", "es", content, NOW.minus(Duration.ofDays(2)));
            item.publish("pablo", true, false, NOW.minus(Duration.ofDays(2)));
            return item;
        }

        static NewsletterIssue issue(String id, int number, NewsletterIssue.Status status, List<Block> blocks) {
            return NewsletterIssue.imported(id, "boletin", number, LocalDate.of(2026, 11, number), null, "Nº " + number,
                    blocks, Map.of(), status, null, null, List.of(), NOW);
        }

        @Test
        void every_site_is_checked_against_its_own_sections_and_routes_and_its_findings_say_which_site() {
            var s = snapshot()
                    .item(inNewsletter("nlp", ItemType.ARTICLE, titled("Del boletín", "nl1", "<a href=\"/intranet/n/boletin/p/nadie\">x</a>"
                            + "<a href=\"/intranet/n/boletin/secciones/nl1\">ok</a><a href=\"/intranet/n/boletin/secciones/s1\">no</a>"
                            + "<a href=\"/intranet/n/boletin/archivo\">ok</a><a href=\"/intranet/n/otro\">no</a>"
                            + "<a href=\"/intranet/n/boletin/numeros/n1\">sin enviar</a><a href=\"/intranet/n/boletin/numeros/n2\">ok</a>",
                            List.of(), null)))
                    .item(published("intra", ItemType.ARTICLE, content("nl1", "", List.of(), null), NOW.minus(Duration.ofDays(2))));
            s.issues.add(issue("n1", 1, NewsletterIssue.Status.DRAFT, List.of()));
            s.issues.add(issue("n2", 2, NewsletterIssue.Status.SENT, List.of()));

            var findings = of(HealthCheck.BROKEN_LINKS, s.build());

            assertThat(findings).filteredOn(f -> f.subjectId().equals("nlp")).allMatch(f -> "boletin".equals(f.siteId()))
                    .extracting(Finding::problem)
                    .hasSize(4)
                    .anyMatch(p -> p.contains("el contenido «nadie» no existe"))
                    .anyMatch(p -> p.contains("la sección «s1» no existe en «Boletín»"))
                    .anyMatch(p -> p.contains("el sitio «otro» no existe"))
                    .anyMatch(p -> p.contains("el número 1 aún no se ha enviado"));
            assertThat(findings).filteredOn(f -> f.subjectId().equals("intra")).singleElement().satisfies(f -> {
                assertThat(f.siteId()).isEqualTo("intranet");
                assertThat(f.problem()).contains("la sección «nl1» no existe en «Intranet»");
            });
        }

        @Test
        void newsletter_issues_still_to_be_sent_have_their_blocks_checked_and_sent_ones_do_not() {
            var broken = List.of(new Block("b", BlockType.QUICK_LINKS, Audience.EVERYONE, "Enlaces", "<img src=\"https://cdn.example/x.png\">",
                    null, null, 3, List.of(new Block.Link("Viejo", "/intranet/noticias/nadie")), null));
            var s = snapshot();
            s.issues.add(issue("n1", 1, NewsletterIssue.Status.DRAFT, broken));
            s.issues.add(issue("n2", 2, NewsletterIssue.Status.SENT, broken));
            var menu = SiteMenu.empty("boletin");
            menu.add("e1", null, Map.of("es", "Último"), new MenuTarget(NewsletterTargets.ISSUE, "n1"), null, true);
            menu.add("e2", null, Map.of("es", "Archivo"), new MenuTarget(NewsletterTargets.ARCHIVE, "boletin"), null, true);
            s.menus.add(menu);

            var built = s.build();
            assertThat(of(HealthCheck.BROKEN_LINKS, built)).filteredOn(f -> f.subject() == Subject.ISSUE).singleElement()
                    .satisfies(f -> {
                        assertThat(f.subjectId()).isEqualTo("n1");
                        assertThat(f.fix()).isEqualTo(Fix.EDIT_ISSUE);
                        assertThat(f.siteId()).isEqualTo("boletin");
                        assertThat(f.subjectTitle()).isEqualTo("Número 1 de «Boletín»: Nº 1");
                    });
            assertThat(of(HealthCheck.BROKEN_LINKS, built)).filteredOn(f -> f.subject() == Subject.MENU).singleElement()
                    .satisfies(f -> assertThat(f.problem()).contains("el número 1 aún no se ha enviado"));
            assertThat(of(HealthCheck.MISSING_ALT, built)).singleElement().satisfies(f -> assertThat(f.subjectId()).isEqualTo("n1"));
        }

        @Test
        void remote_blocks_pages_and_menu_entries_need_an_active_source_of_the_registry() {
            var s = snapshot()
                    .item(published("p", ItemType.PAGE, content("s1", "", List.of(
                            new Block("ok", BlockType.REMOTE, Audience.EVERYONE, "CRS", null, null, null, null, List.of(), null,
                                    RemoteUi.of("crs", "/bookings")),
                            new Block("off", BlockType.REMOTE, Audience.EVERYONE, "Fidelidad", null, null, null, null, List.of(), null,
                                    RemoteUi.of("loyalty", ""))), null), NOW.minus(Duration.ofDays(1))))
                    .item(published("rp", ItemType.REMOTE_PAGE, new ItemContent("s1", Audience.EVERYONE,
                            Map.of("es", new Translation("Pantalla", "", "")), List.of(), null, null, false, false, null,
                            RemoteUi.of("borrada", "/x").uri()), NOW.minus(Duration.ofDays(1))));
            s.uiSources.put("loyalty", false);
            var menu = SiteMenu.empty(SiteMenu.INTRANET);
            menu.add("e", null, Map.of("es", "Fidelidad"), MenuTarget.remote("loyalty", ""), null, true);
            s.menus.add(menu);

            var findings = of(HealthCheck.BROKEN_LINKS, s.build());

            assertThat(findings).extracting(Finding::subjectId).containsExactlyInAnyOrder("p", "rp", "intranet/e");
            assertThat(findings).filteredOn(f -> !f.subjectId().equals("rp"))
                    .allMatch(f -> f.problem().contains("la UI externa «loyalty» está desactivada"));
            assertThat(findings).filteredOn(f -> f.subjectId().equals("rp")).singleElement()
                    .satisfies(f -> assertThat(f.problem()).contains("«borrada» no está (o ya no está) en el registro"));
        }
    }

    @Nested
    class OrphanMedia {

        @Test
        void media_nothing_uses_are_orphans_with_their_size_and_anything_that_uses_them_saves_them() {
            var old = NOW.minus(Duration.ofDays(30));
            var inOldVersion = published("v", "V", "<p>[[media:enVersion]]</p>");
            inOldVersion.editDraft(titled("V", "s1", "<p>sin medio</p>", List.of(), null), NOW);
            inOldVersion.publish("pablo", true, false, NOW);
            var slot = Slot.register("a.b.c", "Slot", "");
            slot.putVariant(new SlotVariant("v1", Audience.EVERYONE, 1, "x", "[[media:enSlot]]", null));
            var b = snapshot()
                    .media(image("huerfano", 3 * 1024 * 1024, old))
                    .media(image("enBorrador", 10, old))
                    .media(image("enVersion", 10, old))
                    .media(image("enSlot", 10, old))
                    .media(image("reciente", 10, NOW.minus(Duration.ofHours(2))))
                    .media(MediaAsset.external("ext", "Vídeo", "https://www.youtube.com/watch?v=aqz-KE-bpKQ", "eva", old))
                    .item(draft("d", "D", "<p>[[media:enBorrador]]</p>"))
                    .item(inOldVersion);
            b.slots.add(slot);

            var orphans = of(HealthCheck.ORPHAN_MEDIA, b.build());

            assertThat(orphans).extracting(Finding::subjectId).containsExactlyInAnyOrder("huerfano", "ext");
            assertThat(orphans).allMatch(f -> f.fix() == Fix.DELETE_MEDIA && f.subject() == Subject.MEDIA);
            assertThat(orphans).filteredOn(f -> f.subjectId().equals("huerfano")).singleElement()
                    .satisfies(f -> {
                        assertThat(f.size()).isEqualTo(3 * 1024 * 1024);
                        assertThat(f.problem()).contains("3,0 MB");
                    });
            assertThat(HealthChecks.orphan(b.build(), "enVersion")).isFalse();
        }
    }

    @Nested
    class MissingAlt {

        @Test
        void library_images_without_a_text_alternative_and_inline_images_without_alt_are_findings() {
            var b = snapshot()
                    .media(image("sinAlt", 10, NOW.minus(Duration.ofDays(5))))
                    .media(image("conAlt", 10, NOW.minus(Duration.ofDays(5))))
                    .item(published("p", ItemType.PAGE, content("s1", "<p>[[media:sinAlt]]<img src=\"https://cdn.example/a.png\">"
                            + "<img src=\"https://cdn.example/b.png\" alt=\"Plano del hotel\"><img src=\"https://cdn.example/c.png\" alt=\" \"></p>",
                            List.of(block("b", BlockType.BANNER, "<img src=\"/x.png\">", null, null, List.of(), null)), null),
                            NOW.minus(Duration.ofDays(1))));
            b.alts.put("conAlt", "Recepción del hotel de Tenerife");

            var findings = of(HealthCheck.MISSING_ALT, b.build());

            assertThat(findings).filteredOn(f -> f.subject() == Subject.MEDIA).singleElement().satisfies(f -> {
                assertThat(f.subjectId()).isEqualTo("sinAlt");
                assertThat(f.fix()).isEqualTo(Fix.SUGGEST_ALT);
                assertThat(f.problem()).contains("se ve en 1 sitio");
            });
            assertThat(findings).filteredOn(f -> f.subject() == Subject.ITEM).extracting(Finding::target)
                    .containsExactlyInAnyOrder("https://cdn.example/a.png", "https://cdn.example/c.png", "/x.png");
        }
    }

    @Nested
    class Expiry {

        @Test
        void published_items_expiring_within_the_horizon_are_findings_and_later_ones_are_not() {
            var s = snapshot()
                    .item(published("pronto", ItemType.NOTICE, content("s1", "", List.of(), NOW.plus(Duration.ofDays(5))),
                            NOW.minus(Duration.ofDays(1))))
                    .item(published("lejos", ItemType.NOTICE, content("s1", "", List.of(), NOW.plus(Duration.ofDays(40))),
                            NOW.minus(Duration.ofDays(1))))
                    .build();
            assertThat(of(HealthCheck.EXPIRY, s)).singleElement().satisfies(f -> {
                assertThat(f.subjectId()).isEqualTo("pronto");
                assertThat(f.problem()).contains("Caduca el 07/11/2026 (en 5 días)");
            });
        }

        @Test
        void expired_items_still_linked_from_pages_homes_and_menus_are_findings_on_whoever_links_them() {
            var expired = published("cad", ItemType.PAGE, titled("Campaña de verano", "s1", "", List.of(),
                    NOW.minus(Duration.ofDays(2))), NOW.minus(Duration.ofDays(60)));
            var menu = SiteMenu.empty(SiteMenu.INTRANET);
            menu.add("verano", null, Map.of("es", "Verano"), MenuTarget.page("cad"), null, true);
            var b = snapshot().item(expired)
                    .item(published("home", ItemType.PAGE, content("s1", "", List.of(
                            block("f", BlockType.FEATURED, null, "cad", null, List.of(), null),
                            block("q", BlockType.QUICK_LINKS, null, null, null, List.of(new Block.Link("Verano", "/intranet/noticias/cad")), null)),
                            null), NOW.minus(Duration.ofDays(10))));
            b.homes.add("cad");
            b.menus.add(menu);

            var findings = of(HealthCheck.EXPIRY, b.build());

            assertThat(findings).extracting(Finding::subject).containsExactlyInAnyOrder(Subject.ITEM, Subject.HOMES, Subject.MENU);
            assertThat(findings).allMatch(f -> f.problem().contains("«Campaña de verano», que caducó el 31/10/2026"));
            assertThat(findings).filteredOn(f -> f.subject() == Subject.ITEM).singleElement()
                    .satisfies(f -> assertThat(f.problem()).contains("también en"));
            // and it is no broken link: expiring is this check's business
            assertThat(of(HealthCheck.BROKEN_LINKS, b.build())).isEmpty();
        }
    }

    @Nested
    class Stale {

        @Test
        void items_not_republished_in_n_months_are_stale_only_in_the_sections_where_freshness_matters() {
            var old = NOW.minus(Duration.ofDays(400));
            var s = snapshot()
                    .item(published("viejo1", ItemType.ARTICLE, content("s1", "", List.of(), null), old))
                    .item(published("viejo2", ItemType.ARTICLE, content("s2", "", List.of(), null), old))
                    .item(published("nuevo", ItemType.ARTICLE, content("s1", "", List.of(), null), NOW.minus(Duration.ofDays(30))))
                    .build();

            assertThat(of(HealthCheck.STALE, s)).extracting(Finding::subjectId).containsExactlyInAnyOrder("viejo1", "viejo2");
            var onlyS1 = new HealthSettings(14, 12, Set.of("s1"), 90, true, Duration.ofSeconds(5), Duration.ofSeconds(1),
                    300, Duration.ofDays(7), Duration.ofDays(1), null, Duration.ofDays(1));
            assertThat(HealthChecks.stale(s, onlyS1)).extracting(Finding::subjectId).containsExactly("viejo1");
        }

        @Test
        void pages_nobody_opened_are_findings_only_with_enough_analytics_and_never_a_home() {
            var b = snapshot()
                    .item(page("olvidada", "Olvidada", List.of()))
                    .item(page("leida", "Leída", List.of()))
                    .item(page("inicio", "Inicio", List.of()))
                    .item(published("nota", "Nota", ""));
            b.homes.add("inicio");
            b.viewed = Set.of("leida");

            b.analyticsSince = null;
            assertThat(of(HealthCheck.STALE, b.build())).isEmpty();

            // analytics for a long time, pages published before the window
            b.now = NOW.plus(Duration.ofDays(120));
            b.analyticsSince = NOW.minus(Duration.ofDays(200));
            assertThat(of(HealthCheck.STALE, b.build())).singleElement().satisfies(f -> {
                assertThat(f.subjectId()).isEqualTo("olvidada");
                assertThat(f.target()).isEqualTo("sin-visitas");
            });

            // analytics for less than two weeks: nothing can be said
            b.analyticsSince = b.now.minus(Duration.ofDays(5));
            assertThat(of(HealthCheck.STALE, b.build())).isEmpty();
        }
    }

    @Test
    void a_findings_key_is_what_it_is_not_how_it_is_worded() {
        var s1 = snapshot().item(published("pronto", ItemType.NOTICE, content("s1", "", List.of(), NOW.plus(Duration.ofDays(5))),
                NOW.minus(Duration.ofDays(1)))).build();
        var tomorrow = snapshot().item(s1.items().getFirst());
        tomorrow.now = NOW.plus(Duration.ofDays(1));
        var today = of(HealthCheck.EXPIRY, s1).getFirst();
        var next = of(HealthCheck.EXPIRY, tomorrow.build()).getFirst();
        assertThat(next.problem()).isNotEqualTo(today.problem());
        assertThat(next.key()).isEqualTo(today.key());
        assertThat(today.key()).startsWith("expiry-");
    }

    @Test
    void urls_are_classified_against_the_portal_host_and_ui_prefixes() {
        var hosts = Set.of(HOST);
        assertThat(ContentReferences.classify("http://ec1.localhost:8800/_portal/intranet/noticias/x/", hosts))
                .isEqualTo(new ContentReferences.Internal("/intranet/noticias/x"));
        assertThat(ContentReferences.classify("https://example.com/a b", hosts))
                .isEqualTo(new ContentReferences.External("https://example.com/a b"));
        assertThat(ContentReferences.classify("//cdn.example/x.png", hosts))
                .isEqualTo(new ContentReferences.External("https://cdn.example/x.png"));
        assertThat(ContentReferences.classify("javascript:alert(1)", hosts)).isInstanceOf(ContentReferences.Ignored.class);
        assertThat(ContentReferences.classify("http://", hosts)).isInstanceOf(ContentReferences.Malformed.class);
        assertThat(ContentReferences.classify("/_intranet/media/m1?h=x", hosts))
                .isEqualTo(new ContentReferences.SignedMedia("m1"));
        assertThat(Instant.parse("2026-11-02T10:00:00Z")).isEqualTo(NOW);
    }
}
