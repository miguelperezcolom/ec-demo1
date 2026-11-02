package io.mateu.ecdemo1.contenthub.application.visibility;

import io.mateu.ecdemo1.contenthub.application.out.ContentIndex;
import io.mateu.ecdemo1.contenthub.application.out.SurveyRepository;
import io.mateu.ecdemo1.contenthub.domain.shared.CollaborationEvents;
import io.mateu.ecdemo1.contenthub.domain.survey.SurveyStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Surveys are subjects of the projection like items: published, each keeps the profile hashes its
 * audience admits ({@code ch_survey_doc}), which the visibility function filters by. Recalculating the
 * whole projection (a named audience redefined) and materialising a new profile cover them through
 * {@link Projection}, as any subject; this projects one survey when it is published, changed or closed.
 * Inline after the commit: it is one subject, milliseconds.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SurveyProjection {

    final ContentIndex index;
    final SurveyRepository surveys;
    final NamedAudiences namedAudiences;

    @Transactional
    public void projectSurvey(String surveyId) {
        var survey = surveys.find(surveyId).orElse(null);
        if (survey == null || survey.status() == SurveyStatus.DRAFT) {
            return;
        }
        var hashes = namedAudiences.policy().hashesSeeing(survey.definition().audience(), index.profiles().values());
        index.indexSurvey(survey, hashes);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(CollaborationEvents.Event event) {
        try {
            switch (event) {
                case CollaborationEvents.SurveyPublished e -> projectSurvey(e.surveyId());
                case CollaborationEvents.SurveyUpdated e -> projectSurvey(e.surveyId());
                case CollaborationEvents.SurveyClosed e -> projectSurvey(e.surveyId());
                default -> {
                }
            }
        } catch (RuntimeException e) {
            log.error("Could not project {}", event, e);
        }
    }
}
