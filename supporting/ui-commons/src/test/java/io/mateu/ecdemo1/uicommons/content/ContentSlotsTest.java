package io.mateu.ecdemo1.uicommons.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ContentSlotsTest {

    @Test
    void theVariantIsDrawnUnderItsTitleAndOverItsLink() throws Exception {
        var slot = ContentSlots.slot(new ObjectMapper().readTree("""
                {"key":"k","variant":"general","title":"Turnos <de> noviembre","html":"<p>Ya está.</p>","link":"/intranet/x"}
                """));
        assertThat(ContentSlots.markup(slot))
                .startsWith("<h3")
                .contains("Turnos &lt;de&gt; noviembre")
                .contains("<p>Ya está.</p>")
                .endsWith("<a href=\"/intranet/x\">Ver más</a></p>");
    }

    @Test
    void noTitleAndNoLinkDrawOnlyTheMarkup() throws Exception {
        var slot = ContentSlots.slot(new ObjectMapper().readTree("{\"html\":\"<p>Hola</p>\"}"));
        assertThat(ContentSlots.markup(slot)).isEqualTo("<p>Hola</p>");
    }

    @Test
    void withoutATokenThereIsNoSlot() {
        assertThat(ContentSlots.fetch(null, "k")).isEmpty();
    }
}
