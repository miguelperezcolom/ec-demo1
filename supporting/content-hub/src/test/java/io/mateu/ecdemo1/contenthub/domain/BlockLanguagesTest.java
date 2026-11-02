package io.mateu.ecdemo1.contenthub.domain;

import io.mateu.ecdemo1.contenthub.application.usecases.HtmlSanitizer;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.media.MediaReferences;
import io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice.BlockForm;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.Json;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A block's text per language: its own fields are the default language's, each other language's in its
 * translations, and a reader of a language gets, part by part, that language's text or the default one's.
 */
class BlockLanguagesTest {

    static final Block.Link BUSCAR = new Block.Link("Buscar", "/intranet/buscar");
    static final Block.Link NOMINAS = new Block.Link("Nóminas", "https://rrhh.example.com");

    static Block links() {
        return new Block("l1", BlockType.QUICK_LINKS, null, "Enlaces útiles", null, null, null, null,
                List.of(BUSCAR, NOMINAS), null);
    }

    static Block html() {
        return new Block("h1", BlockType.HTML, null, "Bienvenida", "<p>Hola a <b>todos</b> [[media:m1]]</p>", null, null,
                null, null, null);
    }

    @Test
    void a_block_stored_before_languages_is_its_default_languages_text_and_reads_the_same() {
        // what the store holds of a block written before blocks had languages: no "translations" at all
        var json = "{\"id\":\"h1\",\"type\":\"HTML\",\"audience\":{\"op\":\"everyone\"},\"title\":\"Bienvenida\","
                + "\"html\":\"<p>Hola</p>\",\"links\":[]}";
        var b = Json.read(json, Block.class);
        assertThat(b.translations()).isEmpty();
        assertThat(b.in("en", "es")).isEqualTo(b);
        assertThat(b.in("en", "es").title()).isEqualTo("Bienvenida");
        assertThat(b.untranslatedIn("en", "es")).isTrue();
        assertThat(b.untranslatedIn("es", "es")).isFalse();
        // and written back with its translations, read back the same (idempotent: nothing else changes)
        var translated = b.withText("en", "es", new Block.BlockText("Welcome", "<p>Hello</p>", null));
        var again = Json.read(Json.write(translated), Block.class);
        assertThat(again).isEqualTo(translated);
        assertThat(Json.read(Json.write(again), Block.class)).isEqualTo(again);
        assertThat(again.in("en", "es").html()).isEqualTo("<p>Hello</p>");
    }

    @Test
    void a_reader_gets_each_part_in_their_language_or_the_default_ones() {
        var b = links().withText("en", "es", new Block.BlockText(null, null, Map.of(BUSCAR.url(), "Search")));
        var en = b.in("en", "es");
        assertThat(en.title()).as("no English title: the default one").isEqualTo("Enlaces útiles");
        assertThat(en.links()).containsExactly(new Block.Link("Search", BUSCAR.url()), NOMINAS);
        assertThat(b.untranslatedIn("en", "es")).as("the title and a label are still Spanish").isTrue();
        // a language it has nothing in, and the default language: as it is
        assertThat(b.in("de", "es")).isEqualTo(b);
        assertThat(b.in("es", "es")).isEqualTo(b);
        var full = b.withText("en", "es", new Block.BlockText("Useful links", null,
                Map.of(BUSCAR.url(), "Search", NOMINAS.url(), "Payslips")));
        assertThat(full.untranslatedIn("en", "es")).isFalse();
        assertThat(full.in("en", "es").links()).extracting(Block.Link::label).containsExactly("Search", "Payslips");
    }

    @Test
    void a_part_left_as_the_fallback_it_was_shown_stays_untranslated() {
        var stored = html().withText("en", "es", new Block.BlockText("Welcome", null, null));
        // the editor in English sees "Welcome" and the Spanish HTML; changes only the title
        var edited = new Block("h1", BlockType.HTML, null, "Welcome all", "<p>Hola a <b>todos</b> [[media:m1]]</p>",
                null, null, null, null, null);
        var merged = Block.edited(stored, edited, "en", "es");
        assertThat(merged.title()).as("the Spanish text stays").isEqualTo("Bienvenida");
        assertThat(merged.html()).isEqualTo("<p>Hola a <b>todos</b> [[media:m1]]</p>");
        assertThat(merged.translations().get("en")).isEqualTo(new Block.BlockText("Welcome all", null, null));
        assertThat(merged.untranslatedIn("en", "es")).as("its HTML is still the Spanish one").isTrue();
        // the HTML as a browser writes it back, untouched: still the fallback
        var rewritten = new Block("h1", BlockType.HTML, null, "Welcome", "<p>Hola a <b>todos</b>&nbsp;[[media:m1]]</p>\n",
                null, null, null, null, null);
        assertThat(Block.edited(stored, rewritten, "en", "es", HtmlSanitizer::same).translations().get("en").html()).isNull();
        // written: it is English now
        var written = new Block("h1", BlockType.HTML, null, "Welcome", "<p>Hello <b>everyone</b> [[media:m1]]</p>",
                null, null, null, null, null);
        assertThat(Block.edited(stored, written, "en", "es").in("en", "es").html()).isEqualTo("<p>Hello <b>everyone</b> [[media:m1]]</p>");
    }

    @Test
    void editing_in_the_default_language_keeps_the_translations_and_the_structure_is_shared() {
        var stored = links().withText("en", "es", new Block.BlockText("Useful links", null,
                Map.of(BUSCAR.url(), "Search", NOMINAS.url(), "Payslips")));
        // in Spanish: links reordered and the title changed
        var edited = new Block("l1", BlockType.QUICK_LINKS, null, "Enlaces", null, null, null, null,
                List.of(NOMINAS, BUSCAR), null);
        var merged = Block.edited(stored, edited, "es", "es");
        assertThat(merged.title()).isEqualTo("Enlaces");
        assertThat(merged.in("en", "es").links()).extracting(Block.Link::label).containsExactly("Payslips", "Search");
        // in English: a link added, the others' labels untouched — the Spanish labels stay; the new one gets its text
        var inEnglish = new Block("l1", BlockType.QUICK_LINKS, null, "Useful links", null, null, null, null,
                List.of(new Block.Link("Payslips", NOMINAS.url()), new Block.Link("Search", BUSCAR.url()),
                        new Block.Link("Canteen", "/intranet/comedor")), null);
        var again = Block.edited(merged, inEnglish, "en", "es");
        assertThat(again.links()).extracting(Block.Link::label).containsExactly("Nóminas", "Buscar", "Canteen");
        assertThat(again.in("en", "es").links()).extracting(Block.Link::label).containsExactly("Payslips", "Search", "Canteen");
    }

    @Test
    void a_new_block_written_in_another_language_is_not_empty_in_the_default_one() {
        var created = new Block("n1", BlockType.HTML, null, "News", "<p>Fresh</p>", null, null, null, null, null);
        var merged = Block.edited(null, created, "en", "es");
        assertThat(merged.title()).isEqualTo("News");
        assertThat(merged.in("en", "es").title()).isEqualTo("News");
        assertThat(merged.untranslatedIn("en", "es")).isFalse();
    }

    @Test
    void the_form_shows_every_language_and_builds_the_same_block_whatever_the_default() {
        var b = links().withText("en", "es", new Block.BlockText("Useful links", null, Map.of(BUSCAR.url(), "Search")))
                .withText("de", "es", new Block.BlockText("Nützliche Links", null, null));
        var form = BlockForm.of(b, "es");
        assertThat(form.titleEs).isEqualTo("Enlaces útiles");
        assertThat(form.titleEn).isEqualTo("Useful links");
        assertThat(form.titleDe).isEqualTo("Nützliche Links");
        assertThat(form.links.getFirst().labelEn).isEqualTo("Search");
        assertThat(form.links.get(1).labelEn).isNull();
        assertThat(form.toBlock("es")).isEqualTo(b);
        assertThat(form.isHidden("htmlEn", null)).as("a quick links block has no HTML in any language").isTrue();
        // the item's default language changes to English: English becomes the block's own text
        var english = form.toBlock("en");
        assertThat(english.title()).isEqualTo("Useful links");
        assertThat(english.in("es", "en").title()).isEqualTo("Enlaces útiles");
        assertThat(english.in("es", "en").links()).extracting(Block.Link::label).containsExactly("Buscar", "Nóminas");
        assertThat(english.links()).extracting(Block.Link::label).containsExactly("Search", NOMINAS.url());
    }

    @Test
    void sanitising_and_media_reach_every_language() {
        var b = html().withText("en", "es", new Block.BlockText(null, "<p onclick=\"x()\">Hi [[media:m2]]</p><script>x()</script>", null));
        var clean = b.mapHtml(HtmlSanitizer::sanitize);
        assertThat(clean.translations().get("en").html()).isEqualTo("<p>Hi [[media:m2]]</p>");
        var content = new ItemContent(null, null, Map.of(), List.of(clean), null, null, false, false, null, null);
        assertThat(MediaReferences.in(content)).containsExactlyInAnyOrder("m1", "m2");
    }

    @Test
    void an_issue_opened_from_its_layout_keeps_the_layouts_languages() {
        var layout = List.of(html().withText("en", "es", new Block.BlockText("Welcome", "<p>Hi</p>", null)));
        var nl = io.mateu.ecdemo1.contenthub.domain.newsletter.Newsletter.create("boletin",
                new io.mateu.ecdemo1.contenthub.domain.newsletter.Newsletter.Settings(layout, null, null, false),
                java.time.Instant.parse("2026-11-02T10:00:00Z"));
        var issue = nl.openIssue("Boletín", null, Map.of(), java.time.Instant.parse("2026-11-02T10:00:00Z"));
        assertThat(issue.blocks().getFirst().id()).isEqualTo("h1-1");
        assertThat(issue.blocks().getFirst().in("en", "es").title()).isEqualTo("Welcome");
    }
}
