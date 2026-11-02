package io.mateu.ecdemo1.contenthub.application;

import io.mateu.ecdemo1.contenthub.application.usecases.BlockTranslation;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * «Traducir con IA» on blocks, with a stubbed translator (never the real AI): what a language lacks of each
 * block — title, HTML, links' labels — goes in one call per language, and comes back in place, the markup and
 * the media marks as they were; what the language already has is not touched; a reply that lost a part
 * changes nothing.
 */
class BlockTranslationTest {

    static final List<Block> BLOCKS = List.of(
            new Block("h", BlockType.HTML, null, "Bienvenida", "<p>Hola a <b>todos</b></p><p>[[media:m1]]</p>", null,
                    null, null, null, null),
            new Block("l", BlockType.QUICK_LINKS, null, "Enlaces", null, null, null, null,
                    List.of(new Block.Link("Buscar", "/intranet/buscar"), new Block.Link("/intranet/x", "/intranet/x")), null)
                    .withText("en", "es", new Block.BlockText("Links", null, null)),
            new Block("g", BlockType.GREETING, null, null, null, null, null, null, null, null));

    @Test
    void what_a_language_lacks_is_translated_in_one_call_keeping_markup_and_media() {
        var translator = new FakeTranslator();
        var result = BlockTranslation.translate(BLOCKS, "es", "en", "Inicio", translator, "Bearer t");
        assertThat(translator.asked).hasSize(1);
        assertThat(translator.asked.getFirst().title()).isEqualTo("Inicio");
        // the HTML's title and body, and one label: the other title is already English, a bare URL is no text
        assertThat(result.parts()).isEqualTo(3);
        var html = result.blocks().getFirst().in("en", "es");
        assertThat(html.title()).isEqualTo("[en] Bienvenida");
        assertThat(html.html()).contains("<p>[en] Hola a <b>[en] todos</b></p>", "<p>[[media:m1]]</p>")
                .doesNotContain("section");
        var links = result.blocks().get(1).in("en", "es");
        assertThat(links.title()).as("what it had stays").isEqualTo("Links");
        assertThat(links.links()).extracting(Block.Link::label).containsExactly("[en] Buscar", "/intranet/x");
        assertThat(result.blocks().get(1).untranslatedIn("en", "es")).isFalse();
        assertThat(result.blocks().get(2)).isEqualTo(BLOCKS.get(2));
        // the default language's text is not touched
        assertThat(result.blocks().getFirst().title()).isEqualTo("Bienvenida");
        // all done: nothing more to ask
        var again = BlockTranslation.translate(result.blocks(), "es", "en", "Inicio", translator, "Bearer t");
        assertThat(again.parts()).isZero();
        assertThat(translator.asked).hasSize(1);
    }

    @Test
    void a_reply_that_lost_a_part_or_changed_its_shape_changes_nothing() {
        var translator = new FakeTranslator();
        translator.body = body -> body.replaceFirst("<h3>[^<]*</h3>", "");
        assertThatThrownBy(() -> BlockTranslation.translate(BLOCKS, "es", "de", "Inicio", translator, null))
                .hasMessageContaining("no se ha cambiado nada");
        translator.body = body -> body.replaceFirst("<h3>", "<p>").replaceFirst("</h3>", "</p>");
        assertThatThrownBy(() -> BlockTranslation.translate(BLOCKS, "es", "de", "Inicio", translator, null))
                .hasMessageContaining("no se ha cambiado nada");
    }

    @Test
    void the_default_language_or_blocks_without_text_ask_nothing() {
        var translator = new FakeTranslator();
        assertThat(BlockTranslation.translate(BLOCKS, "es", "es", "Inicio", translator, null).parts()).isZero();
        assertThat(BlockTranslation.translate(List.of(BLOCKS.get(2)), "es", "en", "Inicio", translator, null).parts()).isZero();
        assertThat(translator.asked).isEmpty();
    }
}
