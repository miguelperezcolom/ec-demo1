package io.mateu.ecdemo1.contenthub.application.usecases;

import io.mateu.ecdemo1.contenthub.application.events.DomainEvents;
import io.mateu.ecdemo1.contenthub.application.out.ContentIndex;
import io.mateu.ecdemo1.contenthub.application.out.SurveyRepository;
import io.mateu.ecdemo1.contenthub.application.out.SurveyResponseRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.visibility.Visibility;
import io.mateu.ecdemo1.contenthub.domain.survey.Answer;
import io.mateu.ecdemo1.contenthub.domain.survey.ParticipationKey;
import io.mateu.ecdemo1.contenthub.domain.survey.Survey;
import io.mateu.ecdemo1.contenthub.domain.survey.SurveyResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Forms and surveys (requirement 1362). Editors write them (content:edit) and open or close them
 * (content:publish: they are seen as soon as they open). Readers see — through the visibility function —
 * the open ones for their audience, and answer each once: an answer cannot be changed afterwards, which
 * is what lets an anonymous answer be stored with nothing that leads back to its author (see
 * {@link ParticipationKey}).
 */
@Service
public class Surveys {

    final SurveyRepository surveys;
    final SurveyResponseRepository responses;
    final Visibility visibility;
    final DomainEvents events;
    final Clock clock;
    final String secret;

    public Surveys(SurveyRepository surveys, SurveyResponseRepository responses, Visibility visibility,
                   DomainEvents events, Clock clock, @Value("${content-hub.surveys.secret:}") String secret) {
        this.surveys = surveys;
        this.responses = responses;
        this.visibility = visibility;
        this.events = events;
        this.clock = clock;
        this.secret = secret;
    }

    // ── editors ─────────────────────────────────────────────────────────────────────────────────

    @Transactional
    public String create(Reader editor, Survey.Definition definition) {
        editor.require(EditorialPermission.EDIT);
        return createWithId("enc-" + UUID.randomUUID().toString().substring(0, 8), editor.userId(), definition);
    }

    /** With a known id: the demo's seed. */
    @Transactional
    public String createWithId(String id, String author, Survey.Definition definition) {
        var salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        var s = Survey.create(id, author, definition, HexFormat.of().formatHex(salt), clock.instant());
        surveys.save(s);
        return id;
    }

    @Transactional
    public void edit(Reader editor, String id, Survey.Definition definition) {
        editor.require(EditorialPermission.EDIT);
        var s = get(id);
        s.edit(definition, clock.instant());
        save(s);
    }

    @Transactional
    public void publish(Reader publisher, String id) {
        publisher.require(EditorialPermission.PUBLISH);
        var s = get(id);
        s.publish(clock.instant());
        save(s);
    }

    @Transactional
    public void close(Reader publisher, String id) {
        publisher.require(EditorialPermission.PUBLISH);
        var s = get(id);
        s.close(clock.instant());
        save(s);
    }

    /** The responses themselves (named surveys say who): for the export. */
    public List<SurveyResponse> responses(Reader editor, String id) {
        editor.require(EditorialPermission.EDIT);
        return responses.of(id);
    }

    public long responseCount(String id) {
        return responses.count(id);
    }

    // ── readers ─────────────────────────────────────────────────────────────────────────────────

    /** An open survey the reader sees, and whether they have answered it. */
    public record Open(ContentIndex.VisibleSurvey survey, boolean answered) {
    }

    public List<Open> open(Reader reader) {
        return visibility.openSurveys(reader.attributes()).stream()
                .map(v -> new Open(v, surveys.find(v.surveyId()).map(s -> answered(reader, s)).orElse(true)))
                .toList();
    }

    /** The open surveys the reader still has to answer. */
    public List<ContentIndex.VisibleSurvey> pending(Reader reader) {
        if (reader.viewingAs() != null) {
            return List.of();
        }
        return open(reader).stream().filter(o -> !o.answered()).map(Open::survey).toList();
    }

    /** The survey, if it is open and for the reader. */
    public Optional<Survey> forReader(Reader reader, String id) {
        return visibility.openSurvey(reader.attributes(), id).flatMap(v -> surveys.find(id));
    }

    public boolean answered(Reader reader, Survey survey) {
        return responses.participated(survey.id(), ParticipationKey.of(survey, reader.userId(), secret));
    }

    @Transactional
    public void answer(Reader reader, String id, Map<String, Answer> answers) {
        if (reader.viewingAs() != null) {
            throw new IllegalStateException("En «ver como» no se responden encuestas");
        }
        var survey = forReader(reader, id)
                .orElseThrow(() -> new SecurityException("Esta encuesta no existe, no está abierta o no es para ti"));
        var key = ParticipationKey.of(survey, reader.userId(), secret);
        var response = SurveyResponse.submit(survey, "r" + UUID.randomUUID(), reader.userId(), reader.name(), answers,
                responses.participated(id, key), clock.instant());
        responses.record(response, key);
        events.drain(response);
    }

    Survey get(String id) {
        return surveys.find(id).orElseThrow(() -> new IllegalArgumentException("No existe la encuesta " + id));
    }

    void save(Survey s) {
        surveys.save(s);
        events.drain(s);
    }
}
