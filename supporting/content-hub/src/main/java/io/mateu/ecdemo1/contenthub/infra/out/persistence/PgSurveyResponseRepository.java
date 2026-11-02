package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.SurveyResponseRepository;
import io.mateu.ecdemo1.contenthub.domain.survey.SurveyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PgSurveyResponseRepository implements SurveyResponseRepository {

    final JdbcTemplate jdbc;

    @Override
    public boolean participated(String surveyId, String key) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from ch_survey_participation "
                + "where survey_id = ? and respondent = ?)", Boolean.class, surveyId, key));
    }

    @Override
    public void record(SurveyResponse response, String key) {
        // the primary key settles two answers sent at the same time: only one row goes in
        var inserted = jdbc.update("insert into ch_survey_participation (survey_id, respondent) values (?, ?) "
                + "on conflict do nothing", response.surveyId(), key);
        if (inserted == 0) {
            throw new IllegalStateException("Ya has respondido a esta encuesta");
        }
        jdbc.update("insert into ch_survey_response (id, survey_id, doc) values (?, ?, ?::jsonb)", response.id(),
                response.surveyId(), Json.write(response));
    }

    @Override
    public List<SurveyResponse> of(String surveyId) {
        // by id (random): never in the order they came in
        return jdbc.query("select doc from ch_survey_response where survey_id = ? order by id",
                (rs, i) -> Json.read(rs.getString(1), SurveyResponse.class), surveyId);
    }

    @Override
    public long count(String surveyId) {
        var n = jdbc.queryForObject("select count(*) from ch_survey_response where survey_id = ?", Long.class, surveyId);
        return n == null ? 0 : n;
    }
}
