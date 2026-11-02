package io.mateu.ecdemo1.contenthub.domain.survey;

import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.shared.AggregateRoot;
import io.mateu.ecdemo1.contenthub.domain.shared.CollaborationEvents;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A form or survey: questions for an audience, open between two dates, anonymous or named.
 *
 * <p>{@code DRAFT → PUBLISHED → CLOSED}. While published and between its dates it is <em>open</em>: the
 * readers of its audience (through the visibility function) answer it, once. Once published its
 * questions and its anonymity are frozen — answers already given must keep meaning what they meant —
 * while its texts, audience and dates can still change.
 */
public class Survey extends AggregateRoot {

    /** What the editor writes. */
    public record Definition(String title, String description, Audience audience, Instant opensAt, Instant closesAt,
                             boolean anonymous, List<Question> questions) {

        public Definition {
            audience = audience == null ? Audience.EVERYONE : audience;
            questions = questions == null ? List.of() : List.copyOf(questions);
        }
    }

    private String id;
    private String author;
    private Definition definition;
    private SurveyStatus status;
    /** Salts the participation keys of an anonymous survey (with the service's secret). */
    private String salt;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant publishedAt;

    @SuppressWarnings("unused") // for the store
    private Survey() {
    }

    public static Survey create(String id, String author, Definition definition, String salt, Instant now) {
        validate(definition);
        var s = new Survey();
        s.id = id;
        s.author = author;
        s.definition = definition;
        s.status = SurveyStatus.DRAFT;
        s.salt = salt;
        s.createdAt = now;
        s.updatedAt = now;
        return s;
    }

    static void validate(Definition d) {
        if (d.title() == null || d.title().isBlank()) {
            throw new IllegalArgumentException("La encuesta necesita un título");
        }
        if (d.opensAt() != null && d.closesAt() != null && !d.closesAt().isAfter(d.opensAt())) {
            throw new IllegalArgumentException("La encuesta tiene que cerrar después de abrirse");
        }
        var ids = new HashSet<String>();
        for (var q : d.questions()) {
            if (!ids.add(q.id())) {
                throw new IllegalArgumentException("Dos preguntas con el mismo id: " + q.id());
            }
        }
    }

    public void edit(Definition d, Instant now) {
        if (status == SurveyStatus.CLOSED) {
            throw new IllegalStateException("Una encuesta cerrada ya no se modifica");
        }
        validate(d);
        if (status == SurveyStatus.PUBLISHED && (!d.questions().equals(definition.questions())
                || d.anonymous() != definition.anonymous())) {
            throw new IllegalStateException("Una encuesta publicada no cambia sus preguntas ni su anonimato");
        }
        definition = d;
        updatedAt = now;
        if (status == SurveyStatus.PUBLISHED) {
            raise(new CollaborationEvents.SurveyUpdated(id));
        }
    }

    public void publish(Instant now) {
        if (status != SurveyStatus.DRAFT) {
            throw new IllegalStateException("Solo se publica un borrador");
        }
        if (definition.questions().isEmpty()) {
            throw new IllegalStateException("Una encuesta sin preguntas no se publica");
        }
        if (definition.closesAt() != null && !definition.closesAt().isAfter(now)) {
            throw new IllegalStateException("La fecha de cierre ya ha pasado");
        }
        status = SurveyStatus.PUBLISHED;
        publishedAt = now;
        updatedAt = now;
        raise(new CollaborationEvents.SurveyPublished(id));
    }

    /** Closes it now (before its closing date, or with none). */
    public void close(Instant now) {
        if (status != SurveyStatus.PUBLISHED) {
            throw new IllegalStateException("Solo se cierra una encuesta publicada");
        }
        status = SurveyStatus.CLOSED;
        if (definition.closesAt() == null || definition.closesAt().isAfter(now)) {
            definition = new Definition(definition.title(), definition.description(), definition.audience(),
                    definition.opensAt(), now, definition.anonymous(), definition.questions());
        }
        updatedAt = now;
        raise(new CollaborationEvents.SurveyClosed(id));
    }

    public boolean isOpen(Instant now) {
        return status == SurveyStatus.PUBLISHED
                && (definition.opensAt() == null || !definition.opensAt().isAfter(now))
                && (definition.closesAt() == null || definition.closesAt().isAfter(now));
    }

    /**
     * Checks a reader's answers against the rules — open, not answered before, every required question
     * answered, options among the question's, one option in a single choice, a rating 1 to 5, text not
     * too long — and returns them keeping only what each question's type uses (empty ones dropped).
     *
     * @param alreadyAnswered whether this reader's participation key is already recorded
     */
    public Map<String, Answer> check(Map<String, Answer> given, boolean alreadyAnswered, Instant now) {
        if (!isOpen(now)) {
            throw new IllegalStateException("Esta encuesta no está abierta");
        }
        if (alreadyAnswered) {
            throw new IllegalStateException("Ya has respondido a esta encuesta");
        }
        var answers = given == null ? Map.<String, Answer>of() : given;
        for (var questionId : answers.keySet()) {
            if (question(questionId) == null) {
                throw new IllegalArgumentException("La encuesta no tiene la pregunta " + questionId);
            }
        }
        var out = new LinkedHashMap<String, Answer>();
        for (var q : definition.questions()) {
            var a = answers.get(q.id());
            var kept = a == null ? null : keep(q, a);
            if (kept == null || kept.isEmpty()) {
                if (q.required()) {
                    throw new IllegalArgumentException("Falta responder «" + q.text() + "»");
                }
                continue;
            }
            out.put(q.id(), kept);
        }
        return out;
    }

    static Answer keep(Question q, Answer a) {
        return switch (q.type()) {
            case SINGLE_CHOICE, MULTIPLE_CHOICE -> {
                var chosen = a.choices().stream().filter(Objects::nonNull).map(String::strip).filter(c -> !c.isEmpty())
                        .distinct().toList();
                for (var c : chosen) {
                    if (!q.options().contains(c)) {
                        throw new IllegalArgumentException("«" + c + "» no es una opción de «" + q.text() + "»");
                    }
                }
                if (q.type() == QuestionType.SINGLE_CHOICE && chosen.size() > 1) {
                    throw new IllegalArgumentException("En «" + q.text() + "» se elige una sola opción");
                }
                yield new Answer(chosen, null, null);
            }
            case RATING -> {
                if (a.rating() != null && (a.rating() < 1 || a.rating() > 5)) {
                    throw new IllegalArgumentException("«" + q.text() + "» se valora de 1 a 5");
                }
                yield new Answer(List.of(), a.rating(), null);
            }
            case FREE_TEXT -> {
                if (a.text() != null && a.text().length() > Answer.MAX_TEXT) {
                    throw new IllegalArgumentException("La respuesta a «" + q.text() + "» tiene como mucho "
                            + Answer.MAX_TEXT + " caracteres");
                }
                yield new Answer(List.of(), null, a.text());
            }
        };
    }

    public Question question(String questionId) {
        return definition.questions().stream().filter(q -> q.id().equals(questionId)).findFirst().orElse(null);
    }

    public String id() {
        return id;
    }

    public String author() {
        return author;
    }

    public Definition definition() {
        return definition;
    }

    public String title() {
        return definition.title();
    }

    public boolean anonymous() {
        return definition.anonymous();
    }

    public List<Question> questions() {
        return definition.questions();
    }

    public SurveyStatus status() {
        return status;
    }

    public String salt() {
        return salt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant publishedAt() {
        return publishedAt;
    }
}
