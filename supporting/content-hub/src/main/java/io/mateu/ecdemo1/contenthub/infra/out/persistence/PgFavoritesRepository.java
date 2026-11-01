package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.FavoritesRepository;
import io.mateu.ecdemo1.contenthub.domain.favorites.Favorites;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PgFavoritesRepository implements FavoritesRepository {

    final JdbcTemplate jdbc;

    @Override
    public Favorites get(String userId) {
        return jdbc.query("select doc from ch_favorites where user_id = ?", (rs, i) -> rs.getString(1), userId).stream()
                .findFirst().map(d -> Json.read(d, Favorites.class)).orElseGet(() -> Favorites.of(userId));
    }

    @Override
    public void save(Favorites favorites) {
        jdbc.update("insert into ch_favorites (user_id, doc) values (?, ?::jsonb) "
                + "on conflict (user_id) do update set doc = excluded.doc", favorites.userId(), Json.write(favorites));
    }
}
