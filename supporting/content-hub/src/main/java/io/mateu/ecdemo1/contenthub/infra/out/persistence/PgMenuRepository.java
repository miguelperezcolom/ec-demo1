package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.MenuRepository;
import io.mateu.ecdemo1.contenthub.domain.menu.SiteMenu;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** The sites' menus: one JSON document per site. */
@Repository
@RequiredArgsConstructor
public class PgMenuRepository implements MenuRepository {

    final JdbcTemplate jdbc;

    @Override
    public Optional<SiteMenu> find(String siteId) {
        return jdbc.query("select doc from ch_menu where site_id = ?", (rs, i) -> rs.getString(1), siteId).stream()
                .findFirst().map(d -> Json.read(d, SiteMenu.class));
    }

    @Override
    public void save(SiteMenu menu) {
        jdbc.update("insert into ch_menu (site_id, doc) values (?, ?::jsonb) "
                + "on conflict (site_id) do update set doc = excluded.doc", menu.siteId(), Json.write(menu));
    }

    @Override
    public boolean saveIfAbsent(SiteMenu menu) {
        return jdbc.update("insert into ch_menu (site_id, doc) values (?, ?::jsonb) on conflict (site_id) do nothing",
                menu.siteId(), Json.write(menu)) > 0;
    }

    @Override
    public List<String> siteIds() {
        return jdbc.queryForList("select site_id from ch_menu order by site_id", String.class);
    }
}
