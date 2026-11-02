package io.mateu.ecdemo1.contenthub.domain.survey;

import java.util.List;

/**
 * The answer to one question: the options chosen (single or multiple choice), a rating (1–5) or a
 * text. Only the part its question's type uses is kept.
 */
public record Answer(List<String> choices, Integer rating, String text) {

    public static final int MAX_TEXT = 2000;

    public Answer {
        choices = choices == null ? List.of() : List.copyOf(choices);
        text = text == null || text.isBlank() ? null : text.strip();
    }

    public static Answer choice(String... options) {
        return new Answer(List.of(options), null, null);
    }

    public static Answer rating(int value) {
        return new Answer(List.of(), value, null);
    }

    public static Answer text(String text) {
        return new Answer(List.of(), null, text);
    }

    public boolean isEmpty() {
        return choices.isEmpty() && rating == null && text == null;
    }
}
