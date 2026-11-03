package io.mateu.ecdemo1.contenthub.domain.item;

import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.media.MediaReferences;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlockTreeTest {

    static Block block(String id, BlockType type, String parent, Integer span) {
        return new Block(id, type, null, null, type == BlockType.HTML ? "<p>" + id + "</p>" : null, null, null, null, null,
                null, null, null, parent, span);
    }

    static Block html(String id, String parent) {
        return block(id, BlockType.HTML, parent, null);
    }

    static List<String> ids(List<Block> blocks) {
        return blocks.stream().map(Block::id).toList();
    }

    @Test
    void a_flat_page_is_left_as_it_is() {
        var blocks = List.of(html("a", null), html("b", null));
        assertThat(BlockTree.normalized(blocks)).isSameAs(blocks);
    }

    @Test
    void each_container_goes_right_before_what_it_holds() {
        var blocks = List.of(html("in-row", "r"), html("top", null), block("r", BlockType.ROW, null, null),
                html("in-col", "c"), block("c", BlockType.COLUMN, "r", 8));

        assertThat(ids(BlockTree.normalized(blocks))).containsExactly("top", "r", "in-row", "c", "in-col");
    }

    @Test
    void what_has_no_valid_place_goes_to_the_top_and_stays_on_the_page() {
        var noParent = html("orphan", "gone");
        var inALeaf = html("in-leaf", "leaf");
        var cycleA = block("x", BlockType.COLUMN, "y", null);
        var cycleB = block("y", BlockType.COLUMN, "x", null);

        var out = BlockTree.normalized(List.of(html("leaf", null), noParent, inALeaf, cycleA, cycleB));

        assertThat(out).hasSize(5).allSatisfy(b -> assertThat(b.parentId()).isNull());
    }

    @Test
    void too_deep_goes_to_the_top() {
        var blocks = List.of(block("r1", BlockType.ROW, null, null), block("c1", BlockType.COLUMN, "r1", null),
                block("r2", BlockType.ROW, "c1", null), html("ok", "r2"), block("c2", BlockType.COLUMN, "r2", null),
                html("deep", "c2"));

        var out = BlockTree.normalized(blocks);

        assertThat(out.stream().filter(b -> b.id().equals("ok")).findFirst().orElseThrow().parentId()).isEqualTo("r2");
        assertThat(out.stream().filter(b -> b.id().equals("deep")).findFirst().orElseThrow().parentId()).isNull();
    }

    @Test
    void a_hidden_container_hides_what_it_holds_even_if_that_is_visible() {
        var blocks = List.of(block("r", BlockType.ROW, null, null), block("c", BlockType.COLUMN, "r", 4), html("deep", "c"),
                html("side", "r"), html("top", null));

        assertThat(ids(BlockTree.shown(blocks, b -> !b.id().equals("c")))).containsExactly("r", "side", "top");
        assertThat(ids(BlockTree.shown(blocks, b -> !b.id().equals("r")))).containsExactly("top");
    }

    @Test
    void a_copy_under_other_ids_keeps_its_places() {
        var copy = BlockTree.withIds(List.of(block("r", BlockType.ROW, null, null), block("a", BlockType.HTML, "r", 8)), id -> id + "-1");

        assertThat(ids(copy)).containsExactly("r-1", "a-1");
        assertThat(copy.get(1).parentId()).isEqualTo("r-1");
        assertThat(copy.get(1).span()).isEqualTo(8);
    }

    @Test
    void a_width_goes_from_1_to_12_and_without_one_they_share() {
        assertThatThrownBy(() -> block("a", BlockType.HTML, "r", 13)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> block("a", BlockType.HTML, "r", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(BlockTree.spanOf(html("a", "r"), 3)).isEqualTo(4);
        assertThat(BlockTree.spanOf(block("a", BlockType.HTML, "r", 8), 3)).isEqualTo(8);
    }

    @Test
    void every_change_of_text_keeps_its_place() {
        var b = new Block("a", BlockType.HTML, Audience.EVERYONE, "T", "<p>x</p>", null, null, null, null, null, null,
                Map.of("en", new Block.BlockText("E", "<p>e</p>", Map.of())), "r", 6);

        for (var changed : List.of(b.withHtml("y"), b.mapHtml(h -> h), b.withId("z"), b.withTranslations(Map.of()), b.in("en", "es"),
                b.withText("en", "es", new Block.BlockText("F", null, Map.of())), b.rebased("es", "en"),
                Block.edited(b, b, "en", "es"))) {
            assertThat(changed.parentId()).isEqualTo("r");
            assertThat(changed.span()).isEqualTo(6);
        }
    }

    @Test
    void the_media_inside_a_row_still_count_as_used() {
        var medium = new Block("m", BlockType.MEDIA, null, null, null, null, null, null, null, "asset-1", null, null, "c", 4);
        var content = new ItemContent(null, null, Map.of(), List.of(block("r", BlockType.ROW, null, null),
                block("c", BlockType.COLUMN, "r", 8), medium), null, null, false, false, null, null);

        assertThat(MediaReferences.in(content)).isEqualTo(Set.of("asset-1"));
    }
}
