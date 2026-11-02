package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.DiscussionRepository;
import io.mateu.ecdemo1.contenthub.domain.collaboration.Discussion;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PgDiscussionRepository implements DiscussionRepository {

    final JdbcTemplate jdbc;

    @Override
    public Discussion get(String itemId) {
        return jdbc.query("select doc from ch_discussion where item_id = ?", (rs, i) -> rs.getString(1), itemId).stream()
                .findFirst().map(d -> Json.read(d, Discussion.class)).orElseGet(() -> Discussion.of(itemId));
    }

    @Override
    public Discussion lockAndGet(String itemId) {
        // an advisory lock, not "for update": the first comment has no row to lock yet
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?::text, 0))", rs -> {
        }, "ch_discussion:" + itemId);
        return get(itemId);
    }

    @Override
    public void save(Discussion d) {
        jdbc.update("insert into ch_discussion (item_id, doc, comments) values (?, ?::jsonb, ?) "
                        + "on conflict (item_id) do update set doc = excluded.doc, comments = excluded.comments",
                d.itemId(), Json.write(d), d.shownCount());
    }

    @Override
    public List<Discussion> withComments() {
        return jdbc.query("select doc from ch_discussion where jsonb_array_length(doc -> 'comments') > 0",
                (rs, i) -> Json.read(rs.getString(1), Discussion.class));
    }
}
