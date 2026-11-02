package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.LikesRepository;
import io.mateu.ecdemo1.contenthub.domain.collaboration.Likes;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PgLikesRepository implements LikesRepository {

    final JdbcTemplate jdbc;

    @Override
    public Likes get(String itemId) {
        return jdbc.query("select doc from ch_likes where item_id = ?", (rs, i) -> rs.getString(1), itemId).stream()
                .findFirst().map(d -> Json.read(d, Likes.class)).orElseGet(() -> Likes.of(itemId));
    }

    @Override
    public Likes lockAndGet(String itemId) {
        // an advisory lock, not "for update": the first like has no row to lock yet
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?::text, 0))", rs -> {
        }, "ch_likes:" + itemId);
        return get(itemId);
    }

    @Override
    public void save(Likes likes) {
        jdbc.update("insert into ch_likes (item_id, doc, likes) values (?, ?::jsonb, ?) "
                        + "on conflict (item_id) do update set doc = excluded.doc, likes = excluded.likes",
                likes.itemId(), Json.write(likes), likes.count());
    }
}
