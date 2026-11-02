package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.survey.SurveyResponse;

import java.util.List;

/**
 * Who answered (by participation key) and what was answered, in two places that do not point at each
 * other: an anonymous response cannot be traced back to its participation record.
 */
public interface SurveyResponseRepository {

    boolean participated(String surveyId, String participationKey);

    /**
     * Records the participation and the response together (the caller's transaction).
     *
     * @throws IllegalStateException when that key has already answered — two answers at the same time
     */
    void record(SurveyResponse response, String participationKey);

    /** The survey's responses, in no meaningful order (never the order they came in). */
    List<SurveyResponse> of(String surveyId);

    long count(String surveyId);
}
