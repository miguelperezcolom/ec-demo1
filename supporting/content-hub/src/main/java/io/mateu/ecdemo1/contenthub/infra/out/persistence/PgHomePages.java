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
    public List<String> pageIds() {
        return jdbc.queryForList("select item_id from ch_home_page order by position", String.class);
    }

    @Override
    @Transactional
    public void save(List<String> pageIds) {
        jdbc.update("delete from ch_home_page");
        for (int i = 0; i < pageIds.size(); i++) {
            jdbc.update("insert into ch_home_page (position, item_id) values (?, ?)", i, pageIds.get(i));
        }
    }
}
