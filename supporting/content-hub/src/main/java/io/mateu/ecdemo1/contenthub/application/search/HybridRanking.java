package io.mateu.ecdemo1.contenthub.application.search;

import io.mateu.ecdemo1.contenthub.application.out.ContentIndex;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Merges the two rankings of the hybrid search — full text (PostgreSQL's ts_rank, by words) and semantic
 * (the passages nearest to the query's embedding, by meaning) — by reciprocal rank fusion: each item scores
 * {@code Σ 1 / (K + rank)} over the lists it appears in. Ranks, not scores, so the two scales never have to
 * be reconciled; an item both lists agree on comes first. Both lists are already visibility-filtered: this
 * only orders what the reader may see.
 */
public final class HybridRanking {

    /** The usual RRF constant: damps the difference between the first ranks. */
    static final int K = 60;

    private HybridRanking() {
    }

    /**
     * @param fullTextRank   the rank in the full-text list (1-based), 0 when not in it
     * @param semanticRank   the rank of the item's best passage among the items of the semantic list, 0 when not in it
     * @param passage        the item's nearest passage, null when it was found only by its words
     */
    public record Hit(String itemId, double score, int fullTextRank, int semanticRank, ContentIndex.Passage passage) {

        public boolean byMeaning() {
            return semanticRank > 0;
        }

        public boolean byWords() {
            return fullTextRank > 0;
        }
    }

    /**
     * @param fullText    item ids, best first
     * @param passages    passages, nearest first (several may be of the same item: its first one counts)
     * @param maxDistance a passage further than this (cosine distance) is not a match by meaning
     */
    public static List<Hit> merge(List<String> fullText, List<ContentIndex.Passage> passages, double maxDistance) {
        var semantic = new LinkedHashMap<String, ContentIndex.Passage>();
        for (var p : passages) {
            if (p.distance() <= maxDistance) {
                semantic.putIfAbsent(p.itemId(), p);
            }
        }
        var ids = new LinkedHashMap<String, int[]>();
        var rank = 0;
        for (var id : fullText) {
            rank++;
            var r = ids.computeIfAbsent(id, k -> new int[2]);
            if (r[0] == 0) {
                r[0] = rank;
            }
        }
        rank = 0;
        for (var id : semantic.keySet()) {
            rank++;
            ids.computeIfAbsent(id, k -> new int[2])[1] = rank;
        }
        var hits = new ArrayList<Hit>();
        ids.forEach((id, r) -> hits.add(new Hit(id, rrf(r[0]) + rrf(r[1]), r[0], r[1], semantic.get(id))));
        hits.sort(Comparator.comparingDouble(Hit::score).reversed()
                // ties: words before meaning (the reader typed them), then the earlier rank
                .thenComparing(h -> h.fullTextRank() == 0 ? Integer.MAX_VALUE : h.fullTextRank())
                .thenComparing(h -> h.semanticRank() == 0 ? Integer.MAX_VALUE : h.semanticRank()));
        return hits;
    }

    static double rrf(int rank) {
        return rank <= 0 ? 0 : 1.0 / (K + rank);
    }
}
