package io.mateu.ecdemo1.contenthub.domain.survey;

import java.util.ArrayList;
import java.util.List;

/**
 * A survey's results, question by question: how many answered it and, by type, how many chose each
 * option (with the percentage of those who answered the question), how the 1–5 ratings spread and their
 * average, or the texts written. Nothing here says who answered.
 */
public record SurveyResults(int responses, List<QuestionResult> questions) {

    public record Count(String label, int count, double percent) {
    }

    /**
     * @param answered how many responses answer this question
     * @param counts   per option (choices) or per value 1..5 (ratings); empty for free text
     * @param average  the ratings' mean; null for other types or no ratings
     * @param texts    the free-text answers, sorted (not in the order they came in, which could link them
     *                 to the same person's other answers); empty for other types
     */
    public record QuestionResult(Question question, int answered, List<Count> counts, Double average,
                                 List<String> texts) {
    }

    public static SurveyResults of(Survey survey, List<SurveyResponse> responses) {
        var out = new ArrayList<QuestionResult>();
        for (var q : survey.questions()) {
            var answers = responses.stream().map(r -> r.answer(q.id())).filter(a -> a != null && !a.isEmpty()).toList();
            var answered = answers.size();
            switch (q.type()) {
                case SINGLE_CHOICE, MULTIPLE_CHOICE -> out.add(new QuestionResult(q, answered, q.options().stream()
                        .map(o -> count(o, (int) answers.stream().filter(a -> a.choices().contains(o)).count(), answered))
                        .toList(), null, List.of()));
                case RATING -> {
                    var counts = new ArrayList<Count>();
                    for (int v = 1; v <= 5; v++) {
                        final int value = v;
                        counts.add(count(String.valueOf(v), (int) answers.stream()
                                .filter(a -> a.rating() != null && a.rating() == value).count(), answered));
                    }
                    var average = answers.stream().filter(a -> a.rating() != null).mapToInt(Answer::rating).average();
                    out.add(new QuestionResult(q, answered, counts, average.isPresent() ? average.getAsDouble() : null,
                            List.of()));
                }
                case FREE_TEXT -> out.add(new QuestionResult(q, answered, List.of(), null,
                        answers.stream().map(Answer::text).sorted().toList()));
            }
        }
        return new SurveyResults(responses.size(), out);
    }

    static Count count(String label, int count, int of) {
        return new Count(label, count, of == 0 ? 0 : Math.round(count * 1000.0 / of) / 10.0);
    }
}
