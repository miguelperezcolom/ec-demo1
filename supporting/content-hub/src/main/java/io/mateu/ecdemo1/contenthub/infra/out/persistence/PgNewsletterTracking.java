package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.NewsletterTracking;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class PgNewsletterTracking implements NewsletterTracking {

    final JdbcTemplate jdbc;

    @Override
    public void record(Event e) {
        jdbc.update("insert into ch_newsletter_event (edition_id, grp, kind, item_id, at) values (?, ?, ?, ?, ?)",
                e.editionId(), e.group(), e.kind().name(), e.itemId(), Timestamp.from(Instant.now()));
    }

    @Override
    public List<GroupStats> stats(String editionId) {
        var opens = new LinkedHashMap<String, Long>();
        var clicks = new LinkedHashMap<String, Map<String, Long>>();
        jdbc.query("select grp, kind, item_id, count(*) from ch_newsletter_event where edition_id = ? "
                + "group by grp, kind, item_id order by grp", rs -> {
            var grp = rs.getString(1);
            opens.putIfAbsent(grp, 0L);
            clicks.putIfAbsent(grp, new LinkedHashMap<>());
            if ("OPEN".equals(rs.getString(2))) {
                opens.merge(grp, rs.getLong(4), Long::sum);
            } else {
                clicks.get(grp).merge(rs.getString(3) == null ? "" : rs.getString(3), rs.getLong(4), Long::sum);
            }
        }, editionId);
        return opens.keySet().stream().map(g -> new GroupStats(g, opens.get(g),
                clicks.get(g).values().stream().mapToLong(Long::longValue).sum(), clicks.get(g))).toList();
    }
}
