package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.domain.navigation.Navigation;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The navigation tree: one JSON document. */
@Repository
@RequiredArgsConstructor
public class PgNavigationRepository implements NavigationRepository {

    final JdbcTemplate jdbc;

    @Override
    public Navigation get(String siteId) {
        return jdbc.query("select doc from ch_navigation where id = ?", (rs, i) -> rs.getString(1), key(siteId)).stream()
                .findFirst().map(d -> Json.read(d, Navigation.class)).orElseGet(Navigation::empty);
    }

    /** The intranet's tree keeps the row it had before sites existed ('main'); a site's is its id. */
    static String key(String siteId) {
        var site = io.mateu.ecdemo1.contenthub.domain.site.Site.of(siteId);
        return io.mateu.ecdemo1.contenthub.domain.site.Site.INTRANET.equals(site) ? "main" : site;
    }

    @Override
    public void save(String siteId, Navigation navigation) {
        jdbc.update("insert into ch_navigation (id, doc) values (?, ?::jsonb) "
                + "on conflict (id) do update set doc = excluded.doc", key(siteId), Json.write(navigation));
    }
}
