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
    public Navigation get() {
        return jdbc.query("select doc from ch_navigation where id = 'main'", (rs, i) -> rs.getString(1)).stream()
                .findFirst().map(d -> Json.read(d, Navigation.class)).orElseGet(Navigation::empty);
    }

    @Override
    public void save(Navigation navigation) {
        jdbc.update("insert into ch_navigation (id, doc) values ('main', ?::jsonb) "
                + "on conflict (id) do update set doc = excluded.doc", Json.write(navigation));
    }
}
