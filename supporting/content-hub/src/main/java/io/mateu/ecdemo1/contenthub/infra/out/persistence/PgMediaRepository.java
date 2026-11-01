package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.MediaRepository;
import io.mateu.ecdemo1.contenthub.domain.media.MediaAsset;
import io.mateu.ecdemo1.contenthub.domain.media.MediaKind;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** The media library; an uploaded file's bytes live in the same row, read only when served. */
@Repository
@RequiredArgsConstructor
public class PgMediaRepository implements MediaRepository {

    static final String COLUMNS = "id, kind, title, file_name, content_type, size, external_url, uploaded_by, uploaded_at";

    final JdbcTemplate jdbc;

    @Override
    public void save(MediaAsset a, byte[] bytes) {
        jdbc.update("insert into ch_media (" + COLUMNS + ", bytes) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "on conflict (id) do update set title = excluded.title",
                a.id(), a.kind().name(), a.title(), a.fileName(), a.contentType(), a.size(), a.externalUrl(),
                a.uploadedBy(), Sql.ts(a.uploadedAt()), bytes);
    }

    @Override
    public Optional<MediaAsset> find(String id) {
        return jdbc.query("select " + COLUMNS + " from ch_media where id = ?", (rs, i) -> map(rs), id).stream().findFirst();
    }

    @Override
    public Optional<byte[]> bytes(String id) {
        return jdbc.query("select bytes from ch_media where id = ? and bytes is not null", (rs, i) -> rs.getBytes(1), id)
                .stream().findFirst();
    }

    @Override
    public List<MediaAsset> search(String text, int offset, int limit) {
        return jdbc.query("select " + COLUMNS + " from ch_media where lower(coalesce(title, '') || ' ' || "
                        + "coalesce(file_name, '') || ' ' || kind) like ? order by uploaded_at desc offset ? limit ?",
                (rs, i) -> map(rs), Sql.like(text), offset, limit);
    }

    @Override
    public long count(String text) {
        var n = jdbc.queryForObject("select count(*) from ch_media where lower(coalesce(title, '') || ' ' || "
                + "coalesce(file_name, '') || ' ' || kind) like ?", Long.class, Sql.like(text));
        return n == null ? 0 : n;
    }

    @Override
    public void delete(String id) {
        jdbc.update("delete from ch_media where id = ?", id);
    }

    static MediaAsset map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new MediaAsset(rs.getString(1), MediaKind.valueOf(rs.getString(2)), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getLong(6), rs.getString(7), rs.getString(8), Sql.instant(rs.getTimestamp(9)));
    }
}
