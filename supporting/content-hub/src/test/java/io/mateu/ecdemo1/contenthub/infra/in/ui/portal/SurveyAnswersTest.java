package io.mateu.ecdemo1.contenthub.infra.in.ui.portal;

import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.survey.Answer;
import io.mateu.ecdemo1.contenthub.domain.survey.Question;
import io.mateu.ecdemo1.contenthub.domain.survey.QuestionType;
import io.mateu.ecdemo1.contenthub.domain.survey.Survey;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SurveyAnswersTest {

    static final Survey SURVEY = Survey.create("enc", "eva", new Survey.Definition("Clima", null, Audience.EVERYONE, null, null,
            false, List.of(new Question("r", "¿Qué tal?", QuestionType.RATING, null, false),
            new Question("s", "¿Sí o no?", QuestionType.SINGLE_CHOICE, List.of("Sí", "No"), false),
            new Question("m", "¿Cuáles?", QuestionType.MULTIPLE_CHOICE, List.of("A", "B"), false),
            new Question("t", "¿Algo más?", QuestionType.FREE_TEXT, null, false))), "sal", Instant.EPOCH);

    @Test
    void each_field_gives_the_answer_of_its_question() {
        var answers = SurveyAnswers.from(SURVEY, Map.of("q_r", "4", "q_s", "Sí", "q_m", List.of("A", "B"), "q_t", " Hola ", "otro", "x"));
        assertThat(answers).containsOnlyKeys("r", "s", "m", "t");
        assertThat(answers.get("r").rating()).isEqualTo(4);
        assertThat(answers.get("s").choices()).containsExactly("Sí");
        assertThat(answers.get("m").choices()).containsExactly("A", "B");
        assertThat(answers.get("t")).isEqualTo(Answer.text("Hola"));
    }

    @Test
    void a_rating_may_arrive_as_a_number_and_nulls_are_no_answer() {
        var state = new HashMap<String, Object>();
        state.put("q_r", 5);
        state.put("q_s", null);
        state.put("q_m", Arrays.asList("A", null));
        var answers = SurveyAnswers.from(SURVEY, state);
        assertThat(answers.get("r").rating()).isEqualTo(5);
        assertThat(answers).doesNotContainKey("s");
        assertThat(answers.get("m").choices()).containsExactly("A");
        assertThat(SurveyAnswers.from(SURVEY, null)).isEmpty();
    }

    @Test
    void a_rating_that_is_no_number_is_refused_and_an_empty_one_is_no_answer() {
        assertThatThrownBy(() -> SurveyAnswers.from(SURVEY, Map.of("q_r", "mucho"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(SurveyAnswers.from(SURVEY, Map.of("q_r", " ")).get("r").isEmpty()).isTrue();
    }
}
