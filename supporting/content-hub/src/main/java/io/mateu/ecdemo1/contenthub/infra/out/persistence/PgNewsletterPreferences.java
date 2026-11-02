package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.NewsletterPreferences;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class PgNewsletterPreferences implements NewsletterPreferences {

    final JdbcTemplate jdbc;

    @Override
    public Preference of(String userId, String newsletterId) {
        return jdbc.query("select subscribed, sections from ch_nl_subscription where user_id = ? and newsletter_id = ?",
                        (rs, i) -> new Preference(rs.getBoolean(1), Set.of((String[]) rs.getArray(2).getArray())),
                        userId, newsletterId)
                .stream().findFirst().orElse(Preference.DEFAULT);
    }

    @Override
    public Map<String, Preference> of(String userId) {
        var out = new LinkedHashMap<String, Preference>();
        jdbc.query("select newsletter_id, subscribed, sections from ch_nl_subscription where user_id = ?", rs -> {
            out.put(rs.getString(1), new Preference(rs.getBoolean(2), Set.of((String[]) rs.getArray(3).getArray())));
        }, userId);
        return out;
    }

    @Override
    public void save(String userId, String newsletterId, Preference p) {
        jdbc.update("insert into ch_nl_subscription (user_id, newsletter_id, subscribed, sections) values (?, ?, ?, ?) "
                        + "on conflict (user_id, newsletter_id) do update set subscribed = excluded.subscribed, "
                        + "sections = excluded.sections",
                ps -> {
                    ps.setString(1, userId);
                    ps.setString(2, newsletterId);
                    ps.setBoolean(3, p.subscribed());
                    ps.setArray(4, ps.getConnection().createArrayOf("text", p.sections().toArray()));
                });
    }
}
