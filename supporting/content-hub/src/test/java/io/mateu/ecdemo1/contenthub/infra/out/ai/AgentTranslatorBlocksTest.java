package io.mateu.ecdemo1.contenthub.infra.out.ai;

import io.mateu.ecdemo1.contenthub.application.FakeTranslator;
import io.mateu.ecdemo1.contenthub.application.usecases.BlockTranslation;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.Json;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The blocks' texts travel to the translator agent as an item's body: the same message, and its reply goes
 * through the same checks — what the agent cannot break without being refused (the parts' markup, the media
 * marks) is what BlockTranslation reads back by position. No real AI: the reply is the stub's.
 */
class AgentTranslatorBlocksTest {

    static final List<Block> BLOCKS = List.of(
            new Block("h", BlockType.HTML, null, "Bienvenida", "<p>Hola <a href=\"https://riu.com\">aquí</a> [[media:m1]]</p>",
                    null, null, null, null, null),
            new Block("l", BlockType.QUICK_LINKS, null, null, null, null, null, null,
                    List.of(new Block.Link("Buscar & ver", "/intranet/buscar")), null));

    /** What BlockTranslation asks for, and the stub's answer to it. */
    static io.mateu.ecdemo1.contenthub.domain.item.Translation[] exchange() {
        var stub = new FakeTranslator();
        BlockTranslation.translate(BLOCKS, "es", "en", "Inicio", stub, null);
        var asked = stub.asked.getFirst();
        return new io.mateu.ecdemo1.contenthub.domain.item.Translation[]{asked, stub.translate(asked, "es", "en", null)};
    }

    @Test
    void the_packed_blocks_pass_the_agents_checks() {
        var exchange = exchange();
        var reply = Json.write(Map.of("title", exchange[1].title(), "summary", "", "body", exchange[1].body()));
        var checked = AgentTranslator.checked(exchange[0], reply);
        assertThat(checked.body()).contains("<h3>[en] Bienvenida</h3>", "[[media:m1]]", "href=\"https://riu.com\"",
                "<p>[en] Buscar &amp; ver</p>");
    }

    @Test
    void a_reply_that_drops_a_media_mark_of_a_block_is_refused() {
        var exchange = exchange();
        var reply = Json.write(Map.of("title", "x", "summary", "", "body", exchange[1].body().replace("[[media:m1]]", "")));
        assertThatThrownBy(() -> AgentTranslator.checked(exchange[0], reply)).hasMessageContaining("media");
    }
}
