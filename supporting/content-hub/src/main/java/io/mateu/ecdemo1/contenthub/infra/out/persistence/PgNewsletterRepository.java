package io.mateu.ecdemo1.contenthub.infra.out.persistence;

import io.mateu.ecdemo1.contenthub.application.out.NewsletterRepository;
import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterEdition;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PgNewsletterRepository implements NewsletterRepository {

    final JdbcTemplate jdbc;

    @Override
    public Optional<NewsletterEdition> find(String id) {
        return jdbc.query("select doc from ch_newsletter where id = ?",
                (rs, i) -> Json.read(rs.getString(1), NewsletterEdition.class), id).stream().findFirst();
    }

    @Override
    public void save(NewsletterEdition edition) {
        jdbc.update("insert into ch_newsletter (id, doc, status, scheduled_at) values (?, ?::jsonb, ?, ?) "
                        + "on conflict (id) do update set doc = excluded.doc, status = excluded.status, "
                        + "scheduled_at = excluded.scheduled_at",
                edition.id(), Json.write(edition), edition.status().name(), Sql.ts(edition.scheduledAt()));
    }

    @Override
    public List<NewsletterEdition> findAll() {
        return jdbc.query("select doc from ch_newsletter order by scheduled_at desc nulls first, id",
                (rs, i) -> Json.read(rs.getString(1), NewsletterEdition.class));
    }

    @Override
    public List<NewsletterEdition> due(Instant now) {
        return jdbc.query("select doc from ch_newsletter where status = 'SCHEDULED' and scheduled_at <= ?",
                (rs, i) -> Json.read(rs.getString(1), NewsletterEdition.class), Sql.ts(now));
    }
}
