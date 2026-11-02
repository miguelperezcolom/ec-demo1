package io.mateu.ecdemo1.contenthub.domain.survey;

import java.util.HashSet;
import java.util.List;

/**
 * One question of a survey. Choice questions have their options (two at least, no repeats); a rating
 * is 1 to 5; free text is up to {@link Answer#MAX_TEXT} characters.
 */
public record Question(String id, String text, QuestionType type, List<String> options, boolean required) {

    public Question {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cada pregunta necesita su id");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Hay una pregunta sin enunciado");
        }
        if (type == null) {
            throw new IllegalArgumentException("La pregunta «" + text + "» no tiene tipo");
        }
        options = options == null ? List.of() : options.stream().map(String::strip).filter(o -> !o.isEmpty()).toList();
        if (type.hasOptions()) {
            if (options.size() < 2) {
                throw new IllegalArgumentException("La pregunta «" + text + "» necesita al menos dos opciones");
            }
            if (new HashSet<>(options).size() != options.size()) {
                throw new IllegalArgumentException("La pregunta «" + text + "» repite una opción");
            }
        } else {
            options = List.of();
        }
    }
}
