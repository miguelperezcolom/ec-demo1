package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PgItemRepository implements ItemRepository {

    static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Europe/Madrid"));

    final JdbcTemplate jdbc;

    @Override
    public Optional<Item> find(String id) {
        return jdbc.query("select doc from ch_item where id = ?", (rs, i) -> Json.read(rs.getString(1), Item.class), id)
                .stream().findFirst();
    }

    @Override
    public void save(Item item) {
        var title = item.draft().in(item.defaultLanguage(), item.defaultLanguage()).title();
        jdbc.update("insert into ch_item (id, doc, type, status, title, author, updated_at, pending_changes, in_review) "
                        + "values (?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?) on conflict (id) do update set doc = excluded.doc, "
                        + "type = excluded.type, status = excluded.status, title = excluded.title, "
                        + "updated_at = excluded.updated_at, pending_changes = excluded.pending_changes, "
                        + "in_review = excluded.in_review",
                item.id(), Json.write(item), item.type().name(), item.status().name(), title, item.author(),
                Sql.ts(item.updatedAt()), item.hasVersions() && item.draftChanged(), item.awaitingPublication());
    }

    @Override
    public List<Item> findAll() {
        return jdbc.query("select doc from ch_item order by updated_at desc", (rs, i) -> Json.read(rs.getString(1), Item.class));
    }

    @Override
    public List<ItemSummary> search(String text, int offset, int limit) {
        return jdbc.query("select id, type, status, title, author, updated_at, pending_changes, in_review from ch_item "
                        + "where lower(coalesce(title, '')) like ? or lower(type) like ? or lower(status) like ? "
                        + "order by updated_at desc offset ? limit ?",
                (rs, i) -> new ItemSummary(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getTimestamp(6) == null ? "" : WHEN.format(rs.getTimestamp(6).toInstant()),
                        rs.getBoolean(7), rs.getBoolean(8)),
                Sql.like(text), Sql.like(text), Sql.like(text), offset, limit);
    }

    @Override
    public long count(String text) {
        var n = jdbc.queryForObject("select count(*) from ch_item where lower(coalesce(title, '')) like ? "
                + "or lower(type) like ? or lower(status) like ?", Long.class, Sql.like(text), Sql.like(text), Sql.like(text));
        return n == null ? 0 : n;
    }
}
