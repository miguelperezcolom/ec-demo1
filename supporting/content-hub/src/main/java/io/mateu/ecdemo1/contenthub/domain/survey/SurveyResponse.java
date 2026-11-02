package io.mateu.ecdemo1.contenthub.domain.survey;

import io.mateu.ecdemo1.contenthub.domain.shared.AggregateRoot;
import io.mateu.ecdemo1.contenthub.domain.shared.CollaborationEvents;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One reader's answers to a survey. In a named survey it says who answered; in an anonymous one it
 * says nothing about them — no id, no name, and the day only, not the instant, so it cannot be matched
 * by time with the participation record ({@link ParticipationKey}) that prevents a second answer.
 */
public class SurveyResponse extends AggregateRoot {

    private String id;
    private String surveyId;
    private String respondentId;
    private String respondentName;
    private Map<String, Answer> answers;
    private Instant submittedAt;

    @SuppressWarnings("unused") // for the store
    private SurveyResponse() {
    }

    /**
     * The answers checked by the survey ({@link Survey#check}): a response that does not pass the rules
     * is never built.
     */
    public static SurveyResponse submit(Survey survey, String id, String userId, String userName,
                                        Map<String, Answer> given, boolean alreadyAnswered, Instant now) {
        var r = new SurveyResponse();
        r.answers = new LinkedHashMap<>(survey.check(given, alreadyAnswered, now));
        r.id = id;
        r.surveyId = survey.id();
        if (!survey.anonymous()) {
            r.respondentId = userId;
            r.respondentName = userName;
        }
        r.submittedAt = survey.anonymous() ? now.truncatedTo(ChronoUnit.DAYS) : now;
        r.raise(new CollaborationEvents.SurveyAnswered(survey.id()));
        return r;
    }

    public String id() {
        return id;
    }

    public String surveyId() {
        return surveyId;
    }

    /** Null in an anonymous survey. */
    public String respondentId() {
        return respondentId;
    }

    /** Null in an anonymous survey. */
    public String respondentName() {
        return respondentName;
    }

    public Map<String, Answer> answers() {
        return Map.copyOf(answers);
    }

    public Answer answer(String questionId) {
        return answers.get(questionId);
    }

    public Instant submittedAt() {
        return submittedAt;
    }
}
