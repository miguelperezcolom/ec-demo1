package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.TemplateRepository;
import io.mateu.ecdemo1.contenthub.domain.template.PageTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PgTemplateRepository implements TemplateRepository {

    final JdbcTemplate jdbc;

    @Override
    public Optional<PageTemplate> find(String id) {
        return jdbc.query("select doc from ch_page_template where id = ?",
                (rs, i) -> Json.read(rs.getString(1), PageTemplate.class), id).stream().findFirst();
    }

    @Override
    public void save(PageTemplate template) {
        jdbc.update("insert into ch_page_template (id, name, doc, updated_at) values (?, ?, ?::jsonb, ?) "
                        + "on conflict (id) do update set name = excluded.name, doc = excluded.doc, updated_at = excluded.updated_at",
                template.id(), template.name(), Json.write(template), Sql.ts(template.updatedAt()));
    }

    @Override
    public void delete(String id) {
        jdbc.update("delete from ch_page_template where id = ?", id);
    }

    @Override
    public List<PageTemplate> findAll() {
        return jdbc.query("select doc from ch_page_template order by lower(name)",
                (rs, i) -> Json.read(rs.getString(1), PageTemplate.class));
    }
}
