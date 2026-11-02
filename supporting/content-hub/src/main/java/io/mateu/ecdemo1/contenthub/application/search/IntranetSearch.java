package io.mateu.ecdemo1.contenthub.application.search;

import io.mateu.ecdemo1.contenthub.application.out.AnswerGenerator;
import io.mateu.ecdemo1.contenthub.application.out.ContentIndex;
import io.mateu.ecdemo1.contenthub.application.out.ContentQuery;
import io.mateu.ecdemo1.contenthub.application.out.Embeddings;
import io.mateu.ecdemo1.contenthub.application.out.VisibleItem;
import io.mateu.ecdemo1.contenthub.application.usecases.HtmlSanitizer;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The portal's search, hybrid: by words (the full-text index) and by meaning (the RAG's passage embeddings,
 * pgvector), merged by {@link HybridRanking}; and, on top, an answer written by the AI from the best
 * passages, citing the items it used.
 *
 * <p>Every list here comes from {@link Visibility} — the items by words, the passages by meaning, the items
 * found only by meaning — so a result, a caption or a passage sent to the model is always something the
 * reader may read. Both AI pieces are optional: without the embeddings service the search is by words only;
 * without the IA control plane's agent, or the reader's token for it, the answer says so and the results stand.
 */
@Service
@Slf4j
public class IntranetSearch {

    /** How many passages the answer is written from. */
    static final int ANSWER_PASSAGES = 6;

    final Visibility visibility;
    final Embeddings embeddings;
    final AnswerGenerator generator;
    final double maxDistance;

    public IntranetSearch(Visibility visibility, Embeddings embeddings, AnswerGenerator generator,
                          @Value("${content-hub.search.max-distance:0.3}") double maxDistance) {
        this.visibility = visibility;
        this.embeddings = embeddings;
        this.generator = generator;
        this.maxDistance = maxDistance;
    }

    // ── the results ─────────────────────────────────────────────────────────────────────────────

    /** A result: the item, the passage that matched (the caption), and whether it matched by meaning. */
    public record Result(VisibleItem item, String caption, boolean byWords, boolean byMeaning) {
    }

    /** @param semantic false when the search by meaning was not available (the embeddings service down) */
    public record Results(List<Result> items, boolean semantic) {
    }

    public Results search(EmployeeAttributes reader, String query, int limit) {
        if (query == null || query.chars().noneMatch(Character::isLetterOrDigit)) {
            return new Results(List.of(), true); // no words: the index would answer with the latest items
        }
        var byWords = visibility.visibles(reader, ContentQuery.search(query, 0, limit)).content();
        var passages = semanticPassages(reader, query, limit * 3);
        var hits = HybridRanking.merge(byWords.stream().map(VisibleItem::itemId).toList(),
                passages == null ? List.of() : passages, maxDistance);
        var known = new LinkedHashMap<String, VisibleItem>();
        byWords.forEach(i -> known.put(i.itemId(), i));
        var missing = hits.stream().map(HybridRanking.Hit::itemId).filter(id -> !known.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            // found only by meaning: the item itself, through the visibility function too
            visibility.visibles(reader, ContentQuery.ids(missing)).content().forEach(i -> known.put(i.itemId(), i));
        }
        var out = new ArrayList<Result>();
        for (var h : hits) {
            var item = known.get(h.itemId());
            if (item == null || out.size() >= limit) {
                continue;
            }
            var caption = h.passage() != null ? Snippets.ofPassage(h.passage().text(), query)
                    : Snippets.around(HtmlSanitizer.text(item.body()), query);
            if (caption.isBlank() && item.summary() != null) {
                caption = item.summary();
            }
            out.add(new Result(item, caption, h.byWords(), h.byMeaning()));
        }
        return new Results(out, passages != null);
    }

    /** The passages nearest to the query among the visible ones; null when the embeddings service fails. */
    List<ContentIndex.Passage> semanticPassages(EmployeeAttributes reader, String query, int limit) {
        try {
            var vector = embeddings.embed(List.of(query), true).getFirst();
            return visibility.similarPassages(reader, vector, limit);
        } catch (RuntimeException e) {
            log.debug("Search by meaning unavailable, by words only: {}", e.getMessage());
            return null;
        }
    }

    // ── the AI's answer ─────────────────────────────────────────────────────────────────────────

    public enum AnswerStatus { ANSWERED, UNAVAILABLE, NOTHING_FOUND, FAILED }

    public record Citation(int number, String itemId, String title) {
    }

    /** What the AI panel shows: an answer with its citations, or a line saying why there is none. */
    public record Answer(AnswerStatus status, String text, List<Citation> citations) {

        static Answer because(AnswerStatus status, String text) {
            return new Answer(status, text, List.of());
        }
    }

    public boolean answersAvailable() {
        return generator.available();
    }

    /** Whatever kept the AI from answering, the reader gets this: the reason is for the log. */
    static final String NOT_AVAILABLE = "La IA no está disponible ahora. Los resultados de abajo siguen siendo válidos.";

    /**
     * An answer to the question from the passages the reader may read — the only ones the agent gets. Its
     * citations are the items of the passages it cites.
     *
     * @param authorization the reader's token: the agent acts for them and their budget is charged. Without
     *                      it there is no answer; no other credential stands in for it.
     */
    public Answer answer(EmployeeAttributes reader, String question, String authorization) {
        if (question == null || question.chars().noneMatch(Character::isLetterOrDigit)) {
            return Answer.because(AnswerStatus.NOTHING_FOUND, "Escribe una pregunta.");
        }
        if (!generator.available() || authorization == null || authorization.isBlank()) {
            log.info("AI answer not offered: {}", generator.available() ? "no token of the reader" : "no agent configured");
            return Answer.because(AnswerStatus.UNAVAILABLE, NOT_AVAILABLE);
        }
        var sources = sources(reader, question);
        if (sources.isEmpty()) {
            return Answer.because(AnswerStatus.NOTHING_FOUND,
                    "No encuentro nada que puedas leer en la intranet sobre esto.");
        }
        String text;
        try {
            text = generator.answer(question, sources, authorization);
        } catch (RuntimeException e) {
            log.info("AI answer failed: {}", e.getMessage());
            return Answer.because(AnswerStatus.FAILED, NOT_AVAILABLE);
        }
        if (text == null || text.isBlank()) {
            return Answer.because(AnswerStatus.FAILED, NOT_AVAILABLE);
        }
        return cited(text, sources);
    }

    /**
     * What the agent may read: the passages nearest to the question among the visible ones; without the
     * embeddings service, the items found by words (their text, cut) — visible ones too.
     */
    List<AnswerGenerator.Source> sources(EmployeeAttributes reader, String question) {
        var passages = semanticPassages(reader, question, ANSWER_PASSAGES * 2);
        if (passages != null) {
            var near = passages.stream().filter(p -> p.distance() <= maxDistance).limit(ANSWER_PASSAGES).toList();
            if (!near.isEmpty()) {
                return near.stream().map(p -> new AnswerGenerator.Source(p.itemId(), p.title(), p.text())).toList();
            }
        }
        return visibility.visibles(reader, ContentQuery.search(question, 0, 3)).content().stream()
                .map(i -> new AnswerGenerator.Source(i.itemId(), i.title(), cut(i.title() + ". "
                        + (i.summary() == null ? "" : i.summary()) + "\n" + HtmlSanitizer.text(i.body()))))
                .toList();
    }

    /** "[1]" or "[1, 3]" in the answer's text: the numbers of the sources it leans on. */
    static final Pattern MARK = Pattern.compile("\\s?\\[(\\d+(?:\\s*,\\s*\\d+)*)]");

    /**
     * The answer with its citations: the items of the passages it used, once each, numbered 1, 2… as first
     * cited, and the marks in the text renumbered to match them (several passages can be of one item). A
     * number that is no source — only what the visibility function let through can be cited — is dropped.
     */
    static Answer cited(String answer, List<AnswerGenerator.Source> sources) {
        var numbers = new LinkedHashMap<String, Integer>();
        var citations = new ArrayList<Citation>();
        var text = MARK.matcher(answer.trim()).replaceAll(m -> {
            var marks = new java.util.LinkedHashSet<String>();
            for (var n : m.group(1).split(",")) {
                var number = n.trim().length() > 4 ? 0 : Integer.parseInt(n.trim());
                if (number >= 1 && number <= sources.size()) {
                    var s = sources.get(number - 1);
                    marks.add(String.valueOf(numbers.computeIfAbsent(s.itemId(), id -> {
                        citations.add(new Citation(citations.size() + 1, id, s.title()));
                        return citations.size();
                    })));
                }
            }
            return marks.isEmpty() ? "" : java.util.regex.Matcher.quoteReplacement(
                    (m.group().startsWith("[") ? "" : " ") + "[" + String.join(", ", marks) + "]");
        });
        return new Answer(AnswerStatus.ANSWERED, text, citations);
    }

    static String cut(String text) {
        var t = text.replaceAll("\\s+", " ").trim();
        return t.length() <= 1500 ? t : t.substring(0, 1500) + "…";
    }

    static final Pattern QUESTION_START = Pattern.compile("^(¿|que|como|cuando|donde|quien|quienes|cual|cuales|"
            + "cuanto|cuantos|cuanta|cuantas|por que|para que|puedo|se puede|hay|tengo|debo|necesito|existe|"
            + "what|how|when|where|who|which|why|can|is|are|do|does|should|"
            + "was|wie|wann|wo|wer|welche|warum|kann|gibt es)\\b");

    /**
     * Whether the query reads as a question (a question mark, a question word first, or a long sentence):
     * then the answer is worth computing at once; otherwise the panel offers «Preguntar a la IA».
     */
    public static boolean looksLikeQuestion(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        var q = Snippets.fold(query.trim());
        return q.contains("?") || q.startsWith("¿") || QUESTION_START.matcher(q.toLowerCase(Locale.ROOT)).find()
                || q.split("\\s+").length >= 6;
    }
}
