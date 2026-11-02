package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.NotificationPreferences;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class PgNotificationPreferences implements NotificationPreferences {

    final JdbcTemplate jdbc;

    @Override
    public Preference of(String userId) {
        return jdbc.query("select in_app, email from ch_notification_pref where user_id = ?",
                (rs, i) -> new Preference(rs.getBoolean(1), rs.getBoolean(2)), userId)
                .stream().findFirst().orElse(Preference.DEFAULT);
    }

    @Override
    public Map<String, Preference> all() {
        var out = new HashMap<String, Preference>();
        jdbc.query("select user_id, in_app, email from ch_notification_pref",
                rs -> {
                    out.put(rs.getString(1), new Preference(rs.getBoolean(2), rs.getBoolean(3)));
                });
        return out;
    }

    @Override
    public void save(String userId, Preference p) {
        jdbc.update("insert into ch_notification_pref (user_id, in_app, email) values (?, ?, ?) "
                + "on conflict (user_id) do update set in_app = excluded.in_app, email = excluded.email",
                userId, p.inApp(), p.email());
    }
}
