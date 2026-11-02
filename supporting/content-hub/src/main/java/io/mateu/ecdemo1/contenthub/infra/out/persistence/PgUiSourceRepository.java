package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.UiSourceRepository;
import io.mateu.ecdemo1.contenthub.domain.uisource.UiSource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * The registry of external UIs, as JSON documents (ch_ui_source) — THE {@code UiSources}: remote pages,
 * remote blocks and the menus' REMOTE entries all resolve against it.
 */
@Repository
@org.springframework.context.annotation.Primary
@RequiredArgsConstructor
public class PgUiSourceRepository implements UiSourceRepository {

    final JdbcTemplate jdbc;

    @Override
    public Optional<UiSource> find(String id) {
        return jdbc.query("select doc from ch_ui_source where id = ?",
                (rs, i) -> Json.read(rs.getString(1), UiSource.class), id).stream().findFirst();
    }

    @Override
    public List<UiSource> all() {
        return jdbc.query("select doc from ch_ui_source order by name, id",
                (rs, i) -> Json.read(rs.getString(1), UiSource.class));
    }

    @Override
    public void save(UiSource source) {
        jdbc.update("insert into ch_ui_source (id, name, doc) values (?, ?, ?::jsonb) "
                        + "on conflict (id) do update set name = excluded.name, doc = excluded.doc",
                source.id(), source.name(), Json.write(source));
    }

    @Override
    public void delete(String id) {
        jdbc.update("delete from ch_ui_source where id = ?", id);
    }
}
