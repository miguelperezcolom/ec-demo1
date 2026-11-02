package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.domain.survey.Answer;
import io.mateu.ecdemo1.contenthub.domain.survey.Question;
import io.mateu.ecdemo1.contenthub.domain.survey.Survey;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * From the survey page's fields, built from the questions, to the answers the domain checks: one field
 * per question, {@code q_<id>}. Here only what each kind of field sends is understood; whether the
 * answers are valid is the survey's to say.
 */
final class SurveyAnswers {

    private SurveyAnswers() {
    }

    static String field(Question question) {
        return "q_" + question.id();
    }

    static Map<String, Answer> from(Survey survey, Map<String, ?> state) {
        var out = new LinkedHashMap<String, Answer>();
        for (var q : survey.questions()) {
            var value = state == null ? null : state.get(field(q));
            if (value == null) {
                continue;
            }
            out.put(q.id(), switch (q.type()) {
                case SINGLE_CHOICE, MULTIPLE_CHOICE -> new Answer(strings(value), null, null);
                case RATING -> new Answer(List.of(), rating(q, value), null);
                case FREE_TEXT -> Answer.text(String.valueOf(value));
            });
        }
        return out;
    }

    static List<String> strings(Object value) {
        if (value instanceof Collection<?> many) {
            return many.stream().filter(Objects::nonNull).map(String::valueOf).toList();
        }
        return List.of(String.valueOf(value));
    }

    static Integer rating(Question q, Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        var text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("«" + q.text() + "» se valora de 1 a 5");
        }
    }
}
