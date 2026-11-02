package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.NewsletterRepository;
import io.mateu.ecdemo1.contenthub.domain.newsletter.Newsletter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PgNewsletterRepository implements NewsletterRepository {

    final JdbcTemplate jdbc;

    @Override
    public Optional<Newsletter> find(String siteId) {
        return jdbc.query("select doc from ch_nl_newsletter where id = ?",
                (rs, i) -> Json.read(rs.getString(1), Newsletter.class), siteId).stream().findFirst();
    }

    @Override
    public void save(Newsletter newsletter) {
        jdbc.update("insert into ch_nl_newsletter (id, doc) values (?, ?::jsonb) on conflict (id) do update set "
                + "doc = excluded.doc", newsletter.id(), Json.write(newsletter));
    }

    @Override
    public List<Newsletter> findAll() {
        return jdbc.query("select doc from ch_nl_newsletter order by id", (rs, i) -> Json.read(rs.getString(1), Newsletter.class));
    }
}
