package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.NamedAudienceRepository;
import io.mateu.ecdemo1.contenthub.domain.audience.NamedAudience;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PgNamedAudienceRepository implements NamedAudienceRepository {

    final JdbcTemplate jdbc;

    @Override
    public Optional<NamedAudience> find(String id) {
        return jdbc.query("select doc from ch_named_audience where id = ?",
                (rs, i) -> Json.read(rs.getString(1), NamedAudience.class), id).stream().findFirst();
    }

    @Override
    public void save(NamedAudience audience) {
        jdbc.update("insert into ch_named_audience (id, name, doc) values (?, ?, ?::jsonb) "
                        + "on conflict (id) do update set name = excluded.name, doc = excluded.doc",
                audience.id(), audience.name(), Json.write(audience));
    }

    @Override
    public void delete(String id) {
        jdbc.update("delete from ch_named_audience where id = ?", id);
    }

    @Override
    public List<NamedAudience> findAll() {
        return jdbc.query("select doc from ch_named_audience order by name",
                (rs, i) -> Json.read(rs.getString(1), NamedAudience.class));
    }
}
