package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.SurveyRepository;
import io.mateu.ecdemo1.contenthub.domain.survey.Survey;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PgSurveyRepository implements SurveyRepository {

    final JdbcTemplate jdbc;

    @Override
    public Optional<Survey> find(String id) {
        return jdbc.query("select doc from ch_survey where id = ?", (rs, i) -> Json.read(rs.getString(1), Survey.class),
                id).stream().findFirst();
    }

    @Override
    public void save(Survey s) {
        jdbc.update("insert into ch_survey (id, doc, status, title, updated_at) values (?, ?::jsonb, ?, ?, ?) "
                        + "on conflict (id) do update set doc = excluded.doc, status = excluded.status, "
                        + "title = excluded.title, updated_at = excluded.updated_at",
                s.id(), Json.write(s), s.status().name(), s.title(), Sql.ts(s.updatedAt()));
    }

    @Override
    public List<Survey> findAll() {
        return jdbc.query("select doc from ch_survey order by updated_at desc nulls last, id",
                (rs, i) -> Json.read(rs.getString(1), Survey.class));
    }
}
