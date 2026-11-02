package io.mateu.ecdemo1.contenthub.domain;

import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.domain.template.PageTemplate;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A template: blocks to start a page from, copied into a new draft; made by hand or from a page. */
class PageTemplateTest {

    static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    static Block html(String id, String title, Audience audience) {
        return new Block(id, BlockType.HTML, audience, title, "<p>" + title + "</p>", null, null, null, null, null);
    }

    @Test
    void a_new_page_starts_with_its_blocks_its_default_audience_and_the_title_given() {
        var hotel = Audience.in(Attribute.HOTEL, "TFS01");
        var t = PageTemplate.create("t1", " Home de equipo ", "Para equipos", List.of(html("b1", "Hola", null),
                html("b2", "Solo recepción", Audience.in(Attribute.DEPARTMENT, "RECEPCION"))), hotel, "pablo", NOW);
        assertThat(t.name()).isEqualTo("Home de equipo");
        var content = t.newPageContent("Recepción TFS01", "es", "operaciones");
        assertThat(content.blocks()).extracting(Block::id).containsExactly("b1", "b2");
        assertThat(content.audience()).isEqualTo(hotel);
        assertThat(content.sectionId()).isEqualTo("operaciones");
        assertThat(content.translations().get("es").title()).isEqualTo("Recepción TFS01");
        assertThat(content.publishAt()).isNull();
    }

    @Test
    void without_a_default_audience_a_page_is_for_everyone() {
        var t = PageTemplate.create("t1", "Landing", "", List.of(), Audience.EVERYONE, "pablo", NOW);
        assertThat(t.defaultAudience()).isNull();
        assertThat(t.newPageContent("Campaña", null, " ").audience()).isEqualTo(Audience.EVERYONE);
        assertThat(t.newPageContent("Campaña", null, " ").sectionId()).isNull();
        assertThatThrownBy(() -> t.newPageContent(" ", "es", null)).hasMessageContaining("título");
    }

    @Test
    void refuses_no_name_repeated_block_ids_and_blocks_that_widen_the_page() {
        assertThatThrownBy(() -> PageTemplate.create("t1", " ", "", List.of(), null, "p", NOW)).hasMessageContaining("nombre");
        assertThatThrownBy(() -> PageTemplate.create("t1", "x", "", List.of(html("b1", "a", null), html("b1", "b", null)),
                null, "p", NOW)).hasMessageContaining("mismo id");
        assertThatThrownBy(() -> PageTemplate.create("t1", "x", "", List.of(html("b1", "a", new Audience.Named("todos"))),
                null, "p", NOW)).hasMessageContaining("estrechar");
    }

    @Test
    void from_a_page_it_takes_the_drafts_blocks_and_audience_not_its_texts() {
        var audience = Audience.in(Attribute.DEPARTMENT, "RECEPCION");
        var page = Item.create("p1", ItemType.PAGE, "eva", "es", new ItemContent("s1", audience,
                Map.of("es", new Translation("Recepción", "Resumen", "<p>cuerpo</p>")),
                List.of(html("b1", "Turnos", null)), null, null, false, false, null, null), NOW);
        var t = PageTemplate.fromPage("t9", page, "Plantilla: Recepción", "De una página", "pablo", NOW);
        assertThat(t.blocks()).extracting(Block::title).containsExactly("Turnos");
        assertThat(t.defaultAudience()).isEqualTo(audience);
        assertThat(t.newPageContent("Nueva", "es", null).translations().get("es").body()).isEmpty();

        var article = Item.create("a1", ItemType.ARTICLE, "eva", "es", new ItemContent("s1", null,
                Map.of("es", new Translation("Noticia", "", "")), List.of(), null, null, false, false, null, null), NOW);
        assertThatThrownBy(() -> PageTemplate.fromPage("t2", article, "x", "", "p", NOW)).hasMessageContaining("página");
    }
}
