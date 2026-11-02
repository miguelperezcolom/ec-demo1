package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.DriveFiles;
import io.mateu.ecdemo1.contenthub.domain.drive.DriveFile;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Drive's metadata of the referenced files, a JSON document per file. */
@Repository
@RequiredArgsConstructor
public class PgDriveFiles implements DriveFiles {

    final JdbcTemplate jdbc;

    @Override
    public void save(DriveFile file) {
        jdbc.update("insert into ch_drive_file (file_id, doc, refreshed_at) values (?, ?::jsonb, ?) "
                        + "on conflict (file_id) do update set doc = excluded.doc, refreshed_at = excluded.refreshed_at",
                file.fileId(), Json.write(file), Sql.ts(file.refreshedAt()));
    }

    @Override
    public Optional<DriveFile> find(String fileId) {
        return jdbc.query("select doc from ch_drive_file where file_id = ?", (rs, i) -> rs.getString(1), fileId)
                .stream().findFirst().map(d -> Json.read(d, DriveFile.class));
    }

    @Override
    public Map<String, DriveFile> findAll(Collection<String> fileIds) {
        var out = new LinkedHashMap<String, DriveFile>();
        if (fileIds.isEmpty()) {
            return out;
        }
        jdbc.query("select doc from ch_drive_file where file_id = any(?::text[])", rs -> {
            var f = Json.read(rs.getString(1), DriveFile.class);
            out.put(f.fileId(), f);
        }, Sql.array(fileIds));
        return out;
    }
}
