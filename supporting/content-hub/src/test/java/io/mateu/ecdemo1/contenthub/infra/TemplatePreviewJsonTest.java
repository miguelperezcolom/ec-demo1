package io.mateu.ecdemo1.contenthub.infra;

import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.domain.preview.PreviewLink;
import io.mateu.ecdemo1.contenthub.domain.preview.PreviewLinkStatus;
import io.mateu.ecdemo1.contenthub.domain.template.PageTemplate;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.Json;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Templates and preview links are stored as JSON documents (ch_page_template, ch_preview_link): they come back whole. */
class TemplatePreviewJsonTest {

    static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    @Test
    void a_template_comes_back_with_its_blocks_and_audience() {
        var t = PageTemplate.create("t1", "Home", "Equipos", List.of(new Block("b1", BlockType.QUICK_LINKS,
                Audience.in(Attribute.HOTEL, "TFS01"), "Enlaces", null, null, null, null,
                List.of(new Block.Link("Buscar", "/intranet/buscar")), null)), Audience.in(Attribute.DEPARTMENT, "RECEPCION"),
                "demo", NOW);
        var back = Json.read(Json.write(t), PageTemplate.class);
        assertThat(back.blocks()).isEqualTo(t.blocks());
        assertThat(back.defaultAudience()).isEqualTo(t.defaultAudience());
        assertThat(back.name()).isEqualTo("Home");
        assertThat(back.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void a_link_comes_back_with_its_views_and_state() {
        var item = Item.create("i1", ItemType.ARTICLE, "eva", "es", new ItemContent("s1", null,
                Map.of("es", new Translation("x", "", "")), List.of(), null, null, false, false, null, null), NOW);
        var link = PreviewLink.create("l1", item, "eva", null, "h1", "HOTEL=TFS01", NOW);
        link.recordView("ana", NOW.plusSeconds(5));
        link.revoke("pablo", NOW.plusSeconds(9));
        var back = Json.read(Json.write(link), PreviewLink.class);
        assertThat(back.views()).containsExactly(new PreviewLink.View("ana", NOW.plusSeconds(5)));
        assertThat(back.viewCount()).isEqualTo(1);
        assertThat(back.viewAsLabel()).isEqualTo("HOTEL=TFS01");
        assertThat(back.status(item, NOW.plusSeconds(10))).isEqualTo(PreviewLinkStatus.REVOKED);
    }
}
