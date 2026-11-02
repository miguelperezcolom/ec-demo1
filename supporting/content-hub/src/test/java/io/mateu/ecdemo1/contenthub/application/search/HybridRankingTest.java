package io.mateu.ecdemo1.contenthub.application.search;

import io.mateu.ecdemo1.contenthub.application.out.ContentIndex;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HybridRankingTest {

    static ContentIndex.Passage passage(String item, double distance) {
        return new ContentIndex.Passage(item, "es", 0, item, "texto " + item, distance);
    }

    @Test
    void what_both_lists_agree_on_comes_first() {
        var hits = HybridRanking.merge(List.of("a", "b", "c"), List.of(passage("x", .1), passage("c", .2)), 1);
        assertThat(hits).extracting(HybridRanking.Hit::itemId).startsWith("c");
        assertThat(hits.getFirst().byWords()).isTrue();
        assertThat(hits.getFirst().byMeaning()).isTrue();
    }

    @Test
    void equal_ranks_prefer_the_words_the_reader_typed() {
        assertThat(HybridRanking.merge(List.of("w"), List.of(passage("m", .1)), 1))
                .extracting(HybridRanking.Hit::itemId).containsExactly("w", "m");
    }

    @Test
    void an_item_counts_once_with_its_nearest_passage() {
        var hits = HybridRanking.merge(List.of(), List.of(passage("a", .1), passage("a", .2), passage("b", .3)), 1);
        assertThat(hits).extracting(HybridRanking.Hit::itemId).containsExactly("a", "b");
        assertThat(hits.getFirst().passage().distance()).isEqualTo(.1);
        assertThat(hits.get(1).semanticRank()).isEqualTo(2);
    }

    @Test
    void a_far_passage_is_no_match_by_meaning() {
        assertThat(HybridRanking.merge(List.of(), List.of(passage("far", .9)), .3)).isEmpty();
        var hits = HybridRanking.merge(List.of("a"), List.of(passage("a", .9)), .3);
        assertThat(hits.getFirst().byMeaning()).isFalse();
        assertThat(hits.getFirst().passage()).isNull();
    }

    @Test
    void words_alone_keep_their_order() {
        assertThat(HybridRanking.merge(List.of("a", "b", "c"), List.of(), .3))
                .extracting(HybridRanking.Hit::itemId).containsExactly("a", "b", "c");
    }
}
