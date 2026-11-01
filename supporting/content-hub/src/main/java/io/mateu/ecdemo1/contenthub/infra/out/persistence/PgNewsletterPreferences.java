package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.NewsletterPreferences;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Set;

@Repository
@RequiredArgsConstructor
public class PgNewsletterPreferences implements NewsletterPreferences {

    final JdbcTemplate jdbc;

    @Override
    public Preference of(String userId) {
        return jdbc.query("select subscribed, sections from ch_newsletter_pref where user_id = ?",
                (rs, i) -> new Preference(rs.getBoolean(1), Set.of((String[]) rs.getArray(2).getArray())), userId)
                .stream().findFirst().orElse(Preference.DEFAULT);
    }

    @Override
    public void save(String userId, Preference p) {
        jdbc.update("insert into ch_newsletter_pref (user_id, subscribed, sections) values (?, ?, ?) "
                        + "on conflict (user_id) do update set subscribed = excluded.subscribed, sections = excluded.sections",
                ps -> {
                    ps.setString(1, userId);
                    ps.setBoolean(2, p.subscribed());
                    ps.setArray(3, ps.getConnection().createArrayOf("text", p.sections().toArray()));
                });
    }
}
