package io.mateu.ecdemo1.contenthub.domain;

import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.shared.CollaborationEvents;
import io.mateu.ecdemo1.contenthub.domain.survey.Answer;
import io.mateu.ecdemo1.contenthub.domain.survey.ParticipationKey;
import io.mateu.ecdemo1.contenthub.domain.survey.Question;
import io.mateu.ecdemo1.contenthub.domain.survey.QuestionType;
import io.mateu.ecdemo1.contenthub.domain.survey.Survey;
import io.mateu.ecdemo1.contenthub.domain.survey.SurveyResponse;
import io.mateu.ecdemo1.contenthub.domain.survey.SurveyResults;
import io.mateu.ecdemo1.contenthub.domain.survey.SurveyStatus;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.Json;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SurveyTest {

    static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    static final Question RATING = new Question("r", "¿Qué tal?", QuestionType.RATING, null, true);
    static final Question SINGLE = new Question("s", "¿Sí o no?", QuestionType.SINGLE_CHOICE, List.of("Sí", "No"), true);
    static final Question MULTIPLE = new Question("m", "¿Cuáles?", QuestionType.MULTIPLE_CHOICE, List.of("A", "B", "C"), false);
    static final Question TEXT = new Question("t", "¿Algo más?", QuestionType.FREE_TEXT, null, false);

    static Survey.Definition definition(boolean anonymous, Instant opens, Instant closes, Question... questions) {
        return new Survey.Definition("Clima", "Dos minutos", Audience.EVERYONE, opens, closes, anonymous, List.of(questions));
    }

    static Survey published(boolean anonymous) {
        var s = Survey.create("enc", "eva", definition(anonymous, null, null, RATING, SINGLE, MULTIPLE, TEXT), "sal", NOW);
        s.publish(NOW);
        s.popEvents();
        return s;
    }

    static Map<String, Answer> valid() {
        return Map.of("r", Answer.rating(4), "s", Answer.choice("Sí"));
    }

    @Test
    void the_lifecycle_goes_draft_published_closed() {
        var s = Survey.create("enc", "eva", definition(false, null, null, RATING), "sal", NOW);
        assertThat(s.status()).isEqualTo(SurveyStatus.DRAFT);
        assertThat(s.isOpen(NOW)).isFalse();
        s.publish(NOW);
        assertThat(s.isOpen(NOW)).isTrue();
        assertThat(s.popEvents()).containsExactly(new CollaborationEvents.SurveyPublished("enc"));
        assertThatThrownBy(() -> s.publish(NOW)).isInstanceOf(IllegalStateException.class);
        s.close(NOW.plusSeconds(1));
        assertThat(s.status()).isEqualTo(SurveyStatus.CLOSED);
        assertThat(s.isOpen(NOW.plusSeconds(2))).isFalse();
        assertThat(s.definition().closesAt()).isEqualTo(NOW.plusSeconds(1));
        assertThatThrownBy(() -> s.close(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> s.edit(s.definition(), NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void it_is_not_published_without_questions_or_with_its_closing_date_past() {
        var empty = Survey.create("enc", "eva", definition(false, null, null), "sal", NOW);
        assertThatThrownBy(() -> empty.publish(NOW)).isInstanceOf(IllegalStateException.class);
        var past = Survey.create("enc", "eva", definition(false, null, NOW.minusSeconds(1), RATING), "sal", NOW);
        assertThatThrownBy(() -> past.publish(NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void definitions_that_make_no_sense_are_refused() {
        assertThatThrownBy(() -> Survey.create("e", "eva", new Survey.Definition(" ", null, null, null, null, false, List.of()), "s", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Survey.create("e", "eva", definition(false, NOW, NOW.minusSeconds(1), RATING), "s", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Survey.create("e", "eva", definition(false, null, null, RATING, RATING), "s", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Question("q", "?", QuestionType.SINGLE_CHOICE, List.of("Solo una"), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Question("q", "?", QuestionType.SINGLE_CHOICE, List.of("A", "A "), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Question("q", " ", QuestionType.RATING, null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void published_its_questions_and_anonymity_are_frozen_but_not_its_texts_and_dates() {
        var s = published(true);
        var d = s.definition();
        assertThatThrownBy(() -> s.edit(new Survey.Definition(d.title(), d.description(), d.audience(), d.opensAt(),
                d.closesAt(), d.anonymous(), List.of(RATING)), NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> s.edit(new Survey.Definition(d.title(), d.description(), d.audience(), d.opensAt(),
                d.closesAt(), false, d.questions()), NOW)).isInstanceOf(IllegalStateException.class);
        s.edit(new Survey.Definition("Otro título", d.description(), d.audience(), d.opensAt(), NOW.plusSeconds(60),
                d.anonymous(), d.questions()), NOW);
        assertThat(s.title()).isEqualTo("Otro título");
        assertThat(s.popEvents()).containsExactly(new CollaborationEvents.SurveyUpdated("enc"));
    }

    @Test
    void it_is_open_between_its_dates() {
        var s = Survey.create("enc", "eva", definition(false, NOW.plusSeconds(10), NOW.plusSeconds(20), RATING), "sal", NOW);
        s.publish(NOW);
        assertThat(s.isOpen(NOW)).isFalse();
        assertThat(s.isOpen(NOW.plusSeconds(10))).isTrue();
        assertThat(s.isOpen(NOW.plusSeconds(20))).isFalse();
    }

    @Test
    void a_closed_or_not_yet_open_survey_refuses_answers() {
        var s = published(false);
        s.close(NOW);
        assertThatThrownBy(() -> SurveyResponse.submit(s, "r", "ana", "Ana", valid(), false, NOW))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no está abierta");
    }

    @Test
    void one_answer_per_reader() {
        assertThatThrownBy(() -> SurveyResponse.submit(published(false), "r", "ana", "Ana", valid(), true, NOW))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Ya has respondido");
    }

    @Test
    void the_required_questions_must_be_answered_and_the_others_need_not() {
        var s = published(false);
        assertThatThrownBy(() -> s.check(Map.of("r", Answer.rating(3)), false, NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("¿Sí o no?");
        assertThatThrownBy(() -> s.check(Map.of("r", Answer.rating(3), "s", new Answer(List.of(" "), null, null)), false, NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("¿Sí o no?");
        assertThat(s.check(valid(), false, NOW)).containsOnlyKeys("r", "s");
    }

    @Test
    void answers_must_fit_their_question() {
        var s = published(false);
        assertThatThrownBy(() -> s.check(Map.of("r", Answer.rating(6), "s", Answer.choice("Sí")), false, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.check(Map.of("r", Answer.rating(0), "s", Answer.choice("Sí")), false, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.check(Map.of("r", Answer.rating(3), "s", Answer.choice("Quizá")), false, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.check(Map.of("r", Answer.rating(3), "s", Answer.choice("Sí", "No")), false, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.check(Map.of("r", Answer.rating(3), "s", Answer.choice("Sí"), "x", Answer.text("?")), false, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        var withTooLong = Map.of("r", Answer.rating(3), "s", Answer.choice("Sí"), "t", Answer.text("x".repeat(Answer.MAX_TEXT + 1)));
        assertThatThrownBy(() -> s.check(withTooLong, false, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void only_what_a_questions_type_uses_is_kept() {
        var s = published(false);
        var kept = s.check(Map.of("r", new Answer(List.of("Sí"), 3, "texto"), "s", Answer.choice("Sí"),
                "m", Answer.choice("A", "A", "C")), false, NOW);
        assertThat(kept.get("r")).isEqualTo(new Answer(List.of(), 3, null));
        assertThat(kept.get("m").choices()).containsExactly("A", "C");
    }

    @Test
    void an_anonymous_response_says_nothing_of_who_nor_exactly_when() {
        var r = SurveyResponse.submit(published(true), "r1", "ana", "Ana Pérez", valid(), false, NOW);
        assertThat(r.respondentId()).isNull();
        assertThat(r.respondentName()).isNull();
        assertThat(r.submittedAt()).isEqualTo(Instant.parse("2026-11-02T00:00:00Z"));
        assertThat(Json.write(r)).doesNotContain("ana").doesNotContain("Ana");
        assertThat(r.popEvents()).containsExactly(new CollaborationEvents.SurveyAnswered("enc"));
    }

    @Test
    void a_named_response_says_who() {
        var r = SurveyResponse.submit(published(false), "r1", "ana", "Ana Pérez", valid(), false, NOW);
        assertThat(r.respondentId()).isEqualTo("ana");
        assertThat(r.respondentName()).isEqualTo("Ana Pérez");
        assertThat(r.submittedAt()).isEqualTo(NOW);
    }

    @Test
    void the_participation_key_of_an_anonymous_survey_is_stable_and_hides_who() {
        var s = published(true);
        var key = ParticipationKey.of(s, "ana", "secret");
        assertThat(key).isEqualTo(ParticipationKey.of(s, "ana", "secret")).doesNotContain("ana");
        assertThat(key).isNotEqualTo(ParticipationKey.of(s, "klaus", "secret"));
        assertThat(key).isNotEqualTo(ParticipationKey.of(s, "ana", "other secret"));
        var another = Survey.create("enc2", "eva", definition(true, null, null, RATING), "other salt", NOW);
        assertThat(ParticipationKey.of(another, "ana", "secret")).isNotEqualTo(key);
        assertThat(ParticipationKey.of(published(false), "ana", "secret")).isEqualTo("u:ana");
    }

    @Test
    void results_per_question() {
        var s = published(true);
        var at = NOW.plus(Duration.ofMinutes(1));
        var responses = List.of(
                SurveyResponse.submit(s, "1", "a", "A", Map.of("r", Answer.rating(5), "s", Answer.choice("Sí"),
                        "m", Answer.choice("A", "B"), "t", Answer.text("Zeta")), false, at),
                SurveyResponse.submit(s, "2", "b", "B", Map.of("r", Answer.rating(4), "s", Answer.choice("Sí"),
                        "m", Answer.choice("A"), "t", Answer.text("Alfa")), false, at),
                SurveyResponse.submit(s, "3", "c", "C", Map.of("r", Answer.rating(3), "s", Answer.choice("No")), false, at));

        var results = SurveyResults.of(s, responses);

        assertThat(results.responses()).isEqualTo(3);
        var rating = results.questions().get(0);
        assertThat(rating.average()).isEqualTo(4.0);
        assertThat(rating.counts()).extracting(SurveyResults.Count::count).containsExactly(0, 0, 1, 1, 1);
        var single = results.questions().get(1);
        assertThat(single.counts()).containsExactly(new SurveyResults.Count("Sí", 2, 66.7), new SurveyResults.Count("No", 1, 33.3));
        // out of those who answered it, and a person may choose several
        var multiple = results.questions().get(2);
        assertThat(multiple.answered()).isEqualTo(2);
        assertThat(multiple.counts()).containsExactly(new SurveyResults.Count("A", 2, 100), new SurveyResults.Count("B", 1, 50),
                new SurveyResults.Count("C", 0, 0));
        // sorted, not in the order they came in
        assertThat(results.questions().get(3).texts()).containsExactly("Alfa", "Zeta");
    }

    @Test
    void results_with_no_answers_divide_by_nothing() {
        var results = SurveyResults.of(published(false), List.of());
        assertThat(results.questions().get(1).counts()).extracting(SurveyResults.Count::percent).containsExactly(0.0, 0.0);
        assertThat(results.questions().getFirst().average()).isNull();
    }

    @Test
    void survey_and_response_survive_being_stored_as_documents() {
        var s = published(true);
        var read = Json.read(Json.write(s), Survey.class);
        assertThat(read.definition()).isEqualTo(s.definition());
        assertThat(read.status()).isEqualTo(SurveyStatus.PUBLISHED);
        assertThat(read.salt()).isEqualTo("sal");

        var r = SurveyResponse.submit(s, "r1", "ana", "Ana", valid(), false, NOW);
        var readResponse = Json.read(Json.write(r), SurveyResponse.class);
        assertThat(readResponse.answers()).isEqualTo(r.answers());
    }
}
