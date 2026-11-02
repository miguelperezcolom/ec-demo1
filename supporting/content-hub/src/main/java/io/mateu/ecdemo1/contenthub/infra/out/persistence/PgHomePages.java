package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.HomePages;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PgHomePages implements HomePages {

    final JdbcTemplate jdbc;

    @Override
    public List<String> pageIds(String siteId) {
        if (intranet(siteId)) {
            return jdbc.queryForList("select item_id from ch_home_page order by position", String.class);
        }
        return jdbc.queryForList("select item_id from ch_site_home_page where site_id = ? order by position", String.class,
                siteId);
    }

    /** The intranet's homes stay where they were before sites existed (ch_home_page); a site's, apart. */
    static boolean intranet(String siteId) {
        return io.mateu.ecdemo1.contenthub.domain.site.Site.INTRANET.equals(io.mateu.ecdemo1.contenthub.domain.site.Site.of(siteId));
    }

    @Override
    @Transactional
    public void save(String siteId, List<String> pageIds) {
        if (!intranet(siteId)) {
            jdbc.update("delete from ch_site_home_page where site_id = ?", siteId);
            for (int i = 0; i < pageIds.size(); i++) {
                jdbc.update("insert into ch_site_home_page (site_id, position, item_id) values (?, ?, ?)", siteId, i,
                        pageIds.get(i));
            }
            return;
        }
        jdbc.update("delete from ch_home_page");
        for (int i = 0; i < pageIds.size(); i++) {
            jdbc.update("insert into ch_home_page (position, item_id) values (?, ?)", i, pageIds.get(i));
        }
    }
}
