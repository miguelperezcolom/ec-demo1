package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.survey.Survey;

import java.util.List;
import java.util.Optional;

public interface SurveyRepository {

    Optional<Survey> find(String id);

    void save(Survey survey);

    /** Most recently changed first. */
    List<Survey> findAll();
}
