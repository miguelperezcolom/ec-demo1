package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.MediaDescriptions;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** ch_media_alt: one text alternative per image of the library. */
@Repository
@RequiredArgsConstructor
public class PgMediaDescriptions implements MediaDescriptions {

    final JdbcTemplate jdbc;

    @Override
    public Optional<String> altText(String mediaId) {
        return jdbc.queryForList("select alt_text from ch_media_alt where media_id = ?", String.class, mediaId)
                .stream().findFirst();
    }

    @Override
    public Map<String, String> all() {
        var out = new LinkedHashMap<String, String>();
        jdbc.query("select media_id, alt_text from ch_media_alt", rs -> {
            out.put(rs.getString(1), rs.getString(2));
        });
        return out;
    }

    @Override
    public void describe(String mediaId, String altText, String by, Instant at) {
        jdbc.update("insert into ch_media_alt (media_id, alt_text, set_by, set_at) values (?, ?, ?, ?) "
                + "on conflict (media_id) do update set alt_text = excluded.alt_text, set_by = excluded.set_by, "
                + "set_at = excluded.set_at", mediaId, altText, by, Sql.ts(at));
    }

    @Override
    public void forget(String mediaId) {
        jdbc.update("delete from ch_media_alt where media_id = ?", mediaId);
    }
}
