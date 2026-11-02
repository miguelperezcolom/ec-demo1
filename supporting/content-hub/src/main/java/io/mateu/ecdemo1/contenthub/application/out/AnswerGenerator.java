package io.mateu.ecdemo1.contenthub.application.out;

import java.util.List;

/**
 * Writes an answer to a reader's question from the passages given — and only from them: they are what the
 * visibility function let through for that reader, so the answer can say nothing the reader may not read.
 * Optional: when it is not {@link #available()} or fails, the search works without it.
 */
public interface AnswerGenerator {

    boolean available();

    /**
     * @param sources       numbered from 1 in the order given; the answer cites them as {@code [n]}
     * @param authorization the reader's own {@code Authorization} header: it is who is charged for the answer
     * @return the answer's text
     * @throws RuntimeException when there is no answer; the caller degrades to "not available"
     */
    String answer(String question, List<Source> sources, String authorization);

    /** A passage the answer may use: which item it comes from, and its text. */
    record Source(String itemId, String title, String text) {
    }
}
