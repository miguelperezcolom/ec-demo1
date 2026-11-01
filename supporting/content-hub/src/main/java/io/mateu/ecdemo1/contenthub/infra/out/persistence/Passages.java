package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuts a translation into the passages the RAG embeds: the title and summary head every passage, so a
 * passage taken alone still says what it is about; the body goes in pieces of about {@link #SIZE}
 * characters, cut at sentence ends where it can, overlapping a little.
 */
final class Passages {

    static final int SIZE = 700;
    static final int OVERLAP = 100;

    private Passages() {
    }

    static List<String> of(String title, String summary, String body) {
        var head = (title + ". " + (summary == null ? "" : summary)).trim();
        var text = body == null ? "" : body.replaceAll("\\s+", " ").trim();
        var out = new ArrayList<String>();
        if (text.isEmpty()) {
            out.add(head);
            return out;
        }
        var start = 0;
        while (start < text.length()) {
            var end = Math.min(text.length(), start + SIZE);
            if (end < text.length()) {
                var dot = text.lastIndexOf(". ", end);
                if (dot > start + SIZE / 2) {
                    end = dot + 1;
                }
            }
            out.add(head + "\n" + text.substring(start, end).trim());
            if (end >= text.length()) {
                break;
            }
            start = Math.max(end - OVERLAP, start + 1);
        }
        return out;
    }
}
