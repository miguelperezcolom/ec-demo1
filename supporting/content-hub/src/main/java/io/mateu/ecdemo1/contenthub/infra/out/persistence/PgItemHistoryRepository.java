package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.ItemHistoryRepository;
import io.mateu.ecdemo1.contenthub.domain.item.HistoryAction;
import io.mateu.ecdemo1.contenthub.domain.item.HistoryEntry;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PgItemHistoryRepository implements ItemHistoryRepository {

    final JdbcTemplate jdbc;

    @Override
    public HistoryEntry append(HistoryEntry entry) {
        // the item's next number in the same statement: two saves of one item at once collide on the
        // primary key and the later one's transaction fails, rather than sharing a number
        var seq = jdbc.queryForObject("insert into ch_item_history (item_id, seq, action, at, by_user, version, note, snapshot) "
                        + "select ?, coalesce(max(seq), 0) + 1, ?, ?, ?, ?, ?, ?::jsonb from ch_item_history where item_id = ? "
                        + "returning seq", Integer.class,
                entry.itemId(), entry.action().name(), Sql.ts(entry.at()), entry.by(), entry.version(), entry.note(),
                entry.snapshot() == null ? null : Json.write(entry.snapshot()), entry.itemId());
        return entry.withSeq(seq == null ? 0 : seq);
    }

    @Override
    public List<HistoryEntry> of(String itemId) {
        return jdbc.query("select item_id, seq, action, at, by_user, version, note, snapshot from ch_item_history "
                        + "where item_id = ? order by seq",
                (rs, i) -> new HistoryEntry(rs.getString(1), rs.getInt(2), HistoryAction.valueOf(rs.getString(3)),
                        Sql.instant(rs.getTimestamp(4)), rs.getString(5), (Integer) rs.getObject(6), rs.getString(7),
                        Json.read(rs.getString(8), ItemContent.class)),
                itemId);
    }
}
