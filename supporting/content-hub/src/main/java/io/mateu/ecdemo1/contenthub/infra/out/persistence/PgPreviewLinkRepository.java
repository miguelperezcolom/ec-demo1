package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.PreviewLinkRepository;
import io.mateu.ecdemo1.contenthub.domain.preview.PreviewLink;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PgPreviewLinkRepository implements PreviewLinkRepository {

    final JdbcTemplate jdbc;

    @Override
    public Optional<PreviewLink> find(String id) {
        return jdbc.query("select doc from ch_preview_link where id = ?",
                (rs, i) -> Json.read(rs.getString(1), PreviewLink.class), id).stream().findFirst();
    }

    @Override
    public void save(PreviewLink link) {
        jdbc.update("insert into ch_preview_link (id, item_id, created_at, doc) values (?, ?, ?, ?::jsonb) "
                        + "on conflict (id) do update set doc = excluded.doc",
                link.id(), link.itemId(), Sql.ts(link.createdAt()), Json.write(link));
    }

    @Override
    public List<PreviewLink> ofItem(String itemId) {
        return jdbc.query("select doc from ch_preview_link where item_id = ? order by created_at desc",
                (rs, i) -> Json.read(rs.getString(1), PreviewLink.class), itemId);
    }

    @Override
    public List<PreviewLink> findAll() {
        return jdbc.query("select doc from ch_preview_link order by created_at desc limit 500",
                (rs, i) -> Json.read(rs.getString(1), PreviewLink.class));
    }

    @Override
    public String signingKey() {
        var bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        // the first one to get here makes it; everyone after reads that one
        jdbc.update("insert into ch_preview_key (id, secret) values (1, ?) on conflict (id) do nothing",
                Base64.getEncoder().encodeToString(bytes));
        return jdbc.queryForObject("select secret from ch_preview_key where id = 1", String.class);
    }
}
