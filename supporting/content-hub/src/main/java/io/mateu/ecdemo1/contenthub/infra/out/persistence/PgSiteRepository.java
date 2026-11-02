package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.SiteRepository;
import io.mateu.ecdemo1.contenthub.domain.site.Site;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PgSiteRepository implements SiteRepository {

    final JdbcTemplate jdbc;

    @Override
    public Optional<Site> find(String id) {
        return jdbc.query("select doc from ch_site where id = ?", (rs, i) -> Json.read(rs.getString(1), Site.class), id)
                .stream().findFirst();
    }

    @Override
    public void save(Site site) {
        jdbc.update("insert into ch_site (id, kind, name, doc) values (?, ?, ?, ?::jsonb) on conflict (id) do update set "
                + "kind = excluded.kind, name = excluded.name, doc = excluded.doc", site.id(), site.kind().name(),
                site.name(), Json.write(site));
    }

    @Override
    public List<Site> findAll() {
        return jdbc.query("select doc from ch_site order by case when id = 'intranet' then 0 else 1 end, name",
                (rs, i) -> Json.read(rs.getString(1), Site.class));
    }
}
