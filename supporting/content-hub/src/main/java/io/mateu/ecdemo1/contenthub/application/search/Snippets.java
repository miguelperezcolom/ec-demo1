package io.mateu.ecdemo1.contenthub.application.search;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;

/**
 * The caption under a search result: the piece of the text that matched — around the first word of the
 * query it contains, or its beginning — short enough for a line or two.
 */
public final class Snippets {

    static final int LENGTH = 220;

    private Snippets() {
    }

    /**
     * A passage as the RAG stores it ("title. summary\nbody piece"): its body piece, around the query's
     * first word in it.
     */
    public static String ofPassage(String passage, String query) {
        if (passage == null) {
            return "";
        }
        var nl = passage.indexOf('\n');
        return around(nl >= 0 ? passage.substring(nl + 1) : passage, query);
    }

    /** Plain text, cut around the first word of the query it contains (by prefix, accents ignored). */
    public static String around(String text, String query) {
        if (text == null || text.isBlank()) {
            return "";
        }
        // a medium's mark is no text to read
        var t = text.replaceAll("\\[\\[media:[^\\]]*]]", " ").replaceAll("\\s+", " ").trim();
        if (t.length() <= LENGTH) {
            return t;
        }
        var folded = fold(t);
        var at = -1;
        for (var w : words(query)) {
            var i = folded.indexOf(w);
            while (i > 0 && Character.isLetterOrDigit(folded.charAt(i - 1))) {
                i = folded.indexOf(w, i + 1);
            }
            if (i >= 0 && (at < 0 || i < at)) {
                at = i;
            }
        }
        var start = at < 0 ? 0 : Math.max(0, at - LENGTH / 3);
        if (start > 0) {
            var space = t.indexOf(' ', start);
            start = space < 0 || space > at ? start : space + 1;
        }
        var end = Math.min(t.length(), start + LENGTH);
        if (end < t.length()) {
            var space = t.lastIndexOf(' ', end);
            end = space > start ? space : end;
        }
        return (start > 0 ? "…" : "") + t.substring(start, end).trim() + (end < t.length() ? "…" : "");
    }

    /** The query's words of three letters or more, folded: what is looked for in the text. */
    static List<String> words(String query) {
        if (query == null) {
            return List.of();
        }
        return Arrays.stream(fold(query).split("[^\\p{L}\\p{N}]+")).filter(w -> w.length() >= 3).toList();
    }

    /** Lower case without accents, keeping the length (one char per char), so positions carry over. */
    static String fold(String s) {
        var sb = new StringBuilder(s.length());
        for (var c : s.toCharArray()) {
            var n = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            sb.append(Character.toLowerCase(n.isEmpty() ? c : n.charAt(0)));
        }
        return sb.toString();
    }
}
